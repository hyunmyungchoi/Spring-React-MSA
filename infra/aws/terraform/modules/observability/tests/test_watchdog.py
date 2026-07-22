import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import Mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "watchdog.py"
SPEC = importlib.util.spec_from_file_location("runtime_watchdog", MODULE_PATH)
WATCHDOG = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(WATCHDOG)


class FakeDynamoDb:
    def __init__(self, states=None):
        self.states = dict(states or {})
        self.puts = []

    def get_item(self, TableName, Key, ConsistentRead):
        del TableName, ConsistentRead
        issue_key = Key["issue_key"]["S"]
        if issue_key not in self.states:
            return {}
        return {"Item": {"active": {"BOOL": self.states[issue_key]}}}

    def put_item(self, TableName, Item):
        del TableName
        issue_key = Item["issue_key"]["S"]
        self.states[issue_key] = Item["active"]["BOOL"]
        self.puts.append(Item)
        return {}


class WatchdogTest(unittest.TestCase):
    NOW = datetime(2026, 7, 22, 0, 0, tzinfo=timezone.utc)

    def build_clients(
        self,
        *,
        asg_desired=0,
        ecs_desired=0,
        runtime_age_hours=0,
        rds_status="stopped",
        restart_hours=48,
        states=None,
    ):
        instance_ids = ["i-test"] if asg_desired > 0 else []
        autoscaling = Mock()
        autoscaling.describe_auto_scaling_groups.return_value = {
            "AutoScalingGroups": [
                {
                    "DesiredCapacity": asg_desired,
                    "Instances": [
                        {"InstanceId": instance_id} for instance_id in instance_ids
                    ],
                }
            ]
        }

        ecs = Mock()
        services = [
            {"desiredCount": 1 if index < ecs_desired else 0}
            for index in range(8)
        ]
        ecs.describe_services.return_value = {"services": services, "failures": []}

        ec2 = Mock()
        ec2.describe_instances.return_value = {
            "Reservations": [
                {
                    "Instances": [
                        {
                            "InstanceId": "i-test",
                            "LaunchTime": self.NOW
                            - timedelta(hours=runtime_age_hours),
                        }
                    ]
                }
            ]
        }

        rds = Mock()
        automatic_restart_time = (
            self.NOW + timedelta(hours=restart_hours)
            if rds_status == "stopped" and restart_hours is not None
            else None
        )
        rds.describe_db_instances.return_value = {
            "DBInstances": [
                {
                    "DBInstanceStatus": rds_status,
                    "AutomaticRestartTime": automatic_restart_time,
                }
            ]
        }

        return {
            "autoscaling": autoscaling,
            "cloudwatch": Mock(),
            "dynamodb": FakeDynamoDb(states),
            "ec2": ec2,
            "ecs": ecs,
            "rds": rds,
            "sns": Mock(),
        }

    @staticmethod
    def config():
        return {
            "asg_name": "learning-asg",
            "ecs_cluster_name": "learning-cluster",
            "ecs_service_names": tuple(f"service-{index}" for index in range(8)),
            "metric_namespace": "spring-react-msa-learning/Watchdog",
            "rds_instance_identifier": "learning-postgres",
            "rds_restart_warning_hours": 24,
            "runtime_max_hours": 6,
            "sns_topic_arn": "arn:aws:sns:ap-northeast-2:111122223333:operations",
            "state_table_name": "watchdog-state",
        }

    def test_healthy_off_state_initializes_without_notification(self):
        clients = self.build_clients()

        result = WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        self.assertFalse(any(result["issues"].values()))
        self.assertEqual({}, result["transitions"])
        clients["sns"].publish.assert_not_called()
        self.assertEqual(3, len(clients["dynamodb"].puts))
        clients["cloudwatch"].put_metric_data.assert_called_once()

    def test_runtime_over_limit_publishes_one_alert(self):
        clients = self.build_clients(
            asg_desired=1,
            ecs_desired=8,
            runtime_age_hours=7,
            rds_status="available",
            restart_hours=None,
        )

        result = WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        self.assertTrue(result["issues"]["runtime-on-too-long"])
        self.assertFalse(result["issues"]["rds-running-while-runtime-off"])
        clients["sns"].publish.assert_called_once()
        self.assertIn("ALERT", clients["sns"].publish.call_args.kwargs["Subject"])

    def test_rds_restart_warning_publishes_alert(self):
        clients = self.build_clients(restart_hours=12)

        result = WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        self.assertTrue(result["issues"]["rds-auto-restart-imminent"])
        clients["sns"].publish.assert_called_once()

    def test_rds_running_while_runtime_off_publishes_alert(self):
        clients = self.build_clients(rds_status="available", restart_hours=None)

        result = WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        self.assertTrue(result["issues"]["rds-running-while-runtime-off"])
        clients["sns"].publish.assert_called_once()

    def test_unchanged_active_issue_does_not_repeat_notification(self):
        clients = self.build_clients(
            asg_desired=1,
            ecs_desired=8,
            runtime_age_hours=7,
            rds_status="available",
            restart_hours=None,
            states={
                "runtime-on-too-long": True,
                "rds-auto-restart-imminent": False,
                "rds-running-while-runtime-off": False,
            },
        )

        WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        clients["sns"].publish.assert_not_called()
        self.assertEqual([], clients["dynamodb"].puts)

    def test_resolved_issue_publishes_recovery(self):
        clients = self.build_clients(
            states={
                "runtime-on-too-long": True,
                "rds-auto-restart-imminent": False,
                "rds-running-while-runtime-off": False,
            }
        )

        WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        clients["sns"].publish.assert_called_once()
        self.assertIn("RECOVERY", clients["sns"].publish.call_args.kwargs["Subject"])
        self.assertFalse(clients["dynamodb"].states["runtime-on-too-long"])

    def test_failed_notification_does_not_commit_transition(self):
        clients = self.build_clients(restart_hours=12)
        clients["sns"].publish.side_effect = RuntimeError("publish failed")

        with self.assertRaisesRegex(RuntimeError, "publish failed"):
            WATCHDOG.run_watchdog(clients, self.config(), self.NOW)

        self.assertNotIn("rds-auto-restart-imminent", clients["dynamodb"].states)
        clients["cloudwatch"].put_metric_data.assert_not_called()


if __name__ == "__main__":
    unittest.main()
