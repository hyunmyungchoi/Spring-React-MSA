"""Alert-only AWS Learning Runtime and RDS lifecycle watchdog."""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from typing import Any


def _utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _runtime_state(clients: dict[str, Any], config: dict[str, Any], now: datetime) -> dict[str, Any]:
    response = clients["autoscaling"].describe_auto_scaling_groups(
        AutoScalingGroupNames=[config["asg_name"]]
    )
    groups = response.get("AutoScalingGroups", [])
    if len(groups) != 1:
        raise RuntimeError("The configured Runtime Auto Scaling Group was not found.")

    group = groups[0]
    asg_desired = int(group.get("DesiredCapacity", 0))
    instance_ids = [
        instance["InstanceId"]
        for instance in group.get("Instances", [])
        if instance.get("InstanceId")
    ]

    services_response = clients["ecs"].describe_services(
        cluster=config["ecs_cluster_name"],
        services=config["ecs_service_names"],
    )
    failures = services_response.get("failures", [])
    if failures:
        raise RuntimeError(f"ECS service inspection failed for {len(failures)} service(s).")

    services = services_response.get("services", [])
    if len(services) != len(config["ecs_service_names"]):
        raise RuntimeError("The watchdog could not inspect all configured ECS services.")

    ecs_desired = sum(int(service.get("desiredCount", 0)) for service in services)
    runtime_active = asg_desired > 0 or ecs_desired > 0
    runtime_age_hours = 0.0

    if runtime_active and instance_ids:
        instances_response = clients["ec2"].describe_instances(InstanceIds=instance_ids)
        launch_times = [
            _utc(instance["LaunchTime"])
            for reservation in instances_response.get("Reservations", [])
            for instance in reservation.get("Instances", [])
            if instance.get("LaunchTime") is not None
        ]
        if launch_times:
            runtime_age_hours = max(
                0.0,
                (now - min(launch_times)).total_seconds() / 3600.0,
            )

    return {
        "active": runtime_active,
        "age_hours": runtime_age_hours,
        "asg_desired": asg_desired,
        "ecs_desired": ecs_desired,
        "instance_count": len(instance_ids),
    }


def _rds_state(clients: dict[str, Any], config: dict[str, Any], now: datetime) -> dict[str, Any]:
    response = clients["rds"].describe_db_instances(
        DBInstanceIdentifier=config["rds_instance_identifier"]
    )
    instances = response.get("DBInstances", [])
    if len(instances) != 1:
        raise RuntimeError("The configured RDS DB instance was not found.")

    instance = instances[0]
    status = str(instance.get("DBInstanceStatus", "unknown"))
    restart_time = instance.get("AutomaticRestartTime")
    restart_hours = None
    if status == "stopped" and restart_time is not None:
        restart_time = _utc(restart_time)
        restart_hours = max(0.0, (restart_time - now).total_seconds() / 3600.0)

    return {
        "status": status,
        "automatic_restart_time": restart_time,
        "restart_hours": restart_hours,
    }


def _get_previous_state(client: Any, table_name: str, issue_key: str) -> bool | None:
    response = client.get_item(
        TableName=table_name,
        Key={"issue_key": {"S": issue_key}},
        ConsistentRead=True,
    )
    item = response.get("Item")
    if not item:
        return None
    return bool(item["active"]["BOOL"])


def _put_state(
    client: Any,
    table_name: str,
    issue_key: str,
    active: bool,
    detail: str,
    now: datetime,
) -> None:
    client.put_item(
        TableName=table_name,
        Item={
            "issue_key": {"S": issue_key},
            "active": {"BOOL": active},
            "detail": {"S": detail},
            "updated_at": {"S": now.isoformat()},
        },
    )


def _publish_transition(
    client: Any,
    topic_arn: str,
    issue_key: str,
    active: bool,
    detail: str,
    now: datetime,
) -> None:
    state = "ALERT" if active else "RECOVERY"
    client.publish(
        TopicArn=topic_arn,
        Subject=f"[Learning Watchdog] {state}: {issue_key}",
        Message=json.dumps(
            {
                "source": "spring-react-msa-learning-runtime-watchdog",
                "state": state,
                "issue": issue_key,
                "detail": detail,
                "detectedAt": now.isoformat(),
                "runbook": "docs/runbooks/aws-observability.md",
            },
            ensure_ascii=False,
            sort_keys=True,
        ),
    )


def _sync_issue(
    clients: dict[str, Any],
    config: dict[str, Any],
    issue_key: str,
    active: bool,
    detail: str,
    now: datetime,
) -> str | None:
    previous = _get_previous_state(
        clients["dynamodb"], config["state_table_name"], issue_key
    )

    if previous == active:
        return None

    transition = None
    if previous is not None or active:
        _publish_transition(
            clients["sns"],
            config["sns_topic_arn"],
            issue_key,
            active,
            detail,
            now,
        )
        transition = "ALERT" if active else "RECOVERY"

    _put_state(
        clients["dynamodb"],
        config["state_table_name"],
        issue_key,
        active,
        detail,
        now,
    )
    return transition


def run_watchdog(
    clients: dict[str, Any],
    config: dict[str, Any],
    now: datetime | None = None,
) -> dict[str, Any]:
    now = _utc(now or datetime.now(timezone.utc))
    runtime = _runtime_state(clients, config, now)
    rds = _rds_state(clients, config, now)

    max_runtime_hours = float(config["runtime_max_hours"])
    warning_hours = float(config["rds_restart_warning_hours"])
    runtime_too_long = runtime["active"] and runtime["age_hours"] >= max_runtime_hours
    restart_imminent = (
        rds["status"] == "stopped"
        and rds["restart_hours"] is not None
        and rds["restart_hours"] <= warning_hours
    )
    rds_unexpectedly_running = (
        not runtime["active"] and rds["status"] not in {"stopped", "stopping"}
    )

    restart_time = rds["automatic_restart_time"]
    issues = {
        "runtime-on-too-long": (
            runtime_too_long,
            "Runtime active for "
            f"{runtime['age_hours']:.2f}h (limit {max_runtime_hours:.0f}h); "
            f"ASG desired={runtime['asg_desired']}, ECS desired={runtime['ecs_desired']}, "
            f"instances={runtime['instance_count']}.",
        ),
        "rds-auto-restart-imminent": (
            restart_imminent,
            "RDS status="
            f"{rds['status']}, automatic restart="
            f"{restart_time.isoformat() if restart_time else 'none'}, "
            f"remaining={rds['restart_hours']:.2f}h."
            if rds["restart_hours"] is not None
            else f"RDS status={rds['status']}, automatic restart=none.",
        ),
        "rds-running-while-runtime-off": (
            rds_unexpectedly_running,
            f"RDS status={rds['status']} while ASG desired={runtime['asg_desired']} "
            f"and ECS desired={runtime['ecs_desired']}.",
        ),
    }

    transitions = {}
    for issue_key, (active, detail) in issues.items():
        transition = _sync_issue(
            clients,
            config,
            issue_key,
            bool(active),
            detail,
            now,
        )
        if transition:
            transitions[issue_key] = transition

    clients["cloudwatch"].put_metric_data(
        Namespace=config["metric_namespace"],
        MetricData=[
            {
                "MetricName": "Heartbeat",
                "Timestamp": now,
                "Value": 1,
                "Unit": "Count",
            }
        ],
    )

    result = {
        "runtime": runtime,
        "rds": {
            "status": rds["status"],
            "automatic_restart_time": restart_time.isoformat() if restart_time else None,
            "restart_hours": rds["restart_hours"],
        },
        "issues": {key: bool(value[0]) for key, value in issues.items()},
        "transitions": transitions,
    }
    print(json.dumps(result, sort_keys=True))
    return result


def lambda_handler(_event: dict[str, Any], _context: Any) -> dict[str, Any]:
    import boto3

    service_names = tuple(
        name.strip()
        for name in os.environ["ECS_SERVICE_NAMES"].split(",")
        if name.strip()
    )
    if len(service_names) != 8:
        raise RuntimeError("ECS_SERVICE_NAMES must contain exactly eight services.")

    config = {
        "asg_name": os.environ["ASG_NAME"],
        "ecs_cluster_name": os.environ["ECS_CLUSTER_NAME"],
        "ecs_service_names": service_names,
        "metric_namespace": os.environ["METRIC_NAMESPACE"],
        "rds_instance_identifier": os.environ["RDS_INSTANCE_IDENTIFIER"],
        "rds_restart_warning_hours": float(os.environ["RDS_RESTART_WARNING_HOURS"]),
        "runtime_max_hours": float(os.environ["RUNTIME_MAX_HOURS"]),
        "sns_topic_arn": os.environ["SNS_TOPIC_ARN"],
        "state_table_name": os.environ["STATE_TABLE_NAME"],
    }
    clients = {
        service: boto3.client(service)
        for service in (
            "autoscaling",
            "cloudwatch",
            "dynamodb",
            "ec2",
            "ecs",
            "rds",
            "sns",
        )
    }
    return run_watchdog(clients, config)
