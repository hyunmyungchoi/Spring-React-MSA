#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
from pathlib import Path


BACKEND_IMAGES: list[dict[str, str]] = [
    {
        "service": "spring-member-gateway",
        "context": "BackEnd/spring-member-gateway",
        "dockerfile": "BackEnd/spring-member-gateway/Dockerfile",
    },
    {
        "service": "spring-admin-gateway",
        "context": "BackEnd/spring-admin-gateway",
        "dockerfile": "BackEnd/spring-admin-gateway/Dockerfile",
    },
    {
        "service": "spring-security-authorization-server",
        "context": "BackEnd",
        "dockerfile": "BackEnd/spring-security-authorization-server/Dockerfile",
    },
    {
        "service": "spring-user-service",
        "context": "BackEnd",
        "dockerfile": "BackEnd/spring-user-service/Dockerfile",
    },
    {
        "service": "spring-member-community-service",
        "context": "BackEnd/spring-member-community-service",
        "dockerfile": "BackEnd/spring-member-community-service/Dockerfile",
    },
    {
        "service": "spring-member-stock-service",
        "context": "BackEnd",
        "dockerfile": "BackEnd/spring-member-stock-service/Dockerfile",
    },
    {
        "service": "spring-member-bff-service",
        "context": "BackEnd",
        "dockerfile": "BackEnd/spring-member-bff-service/Dockerfile",
    },
    {
        "service": "spring-admin-bff-service",
        "context": "BackEnd",
        "dockerfile": "BackEnd/spring-admin-bff-service/Dockerfile",
    },
]

FRONTEND_IMAGES: list[dict[str, str]] = [
    {
        "service": "spring-member-web",
        "dockerfile": "FrontEnd/apps/member/Dockerfile.member",
    },
    {
        "service": "spring-community-web",
        "dockerfile": "FrontEnd/apps/member/Dockerfile.community",
    },
    {
        "service": "spring-stock-web",
        "dockerfile": "FrontEnd/apps/member/Dockerfile.stock",
    },
    {
        "service": "spring-admin-web",
        "dockerfile": "FrontEnd/apps/admin/Dockerfile.admin",
    },
    {
        "service": "spring-admin-users-web",
        "dockerfile": "FrontEnd/apps/admin/Dockerfile.users",
    },
    {
        "service": "spring-admin-logs-web",
        "dockerfile": "FrontEnd/apps/admin/Dockerfile.logs",
    },
]

ZERO_SHA = "0" * 40


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Select GHCR build matrix entries.")
    parser.add_argument("--event-name", default=os.environ.get("GITHUB_EVENT_NAME", "workflow_dispatch"))
    parser.add_argument("--deploy-target", default=os.environ.get("DEPLOY_TARGET", "all"))
    parser.add_argument("--before-sha", default=os.environ.get("BEFORE_SHA", ""))
    parser.add_argument("--current-sha", default=os.environ.get("GITHUB_SHA", ""))
    parser.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT"))
    parser.add_argument("--changed-file", action="append", default=[], help="Changed file path. Repeatable.")
    parser.add_argument("--print-json", action="store_true", help="Print selected matrix data as JSON.")
    return parser.parse_args()


def service_order() -> list[str]:
    return [item["service"] for item in BACKEND_IMAGES + FRONTEND_IMAGES]


def changed_files_from_git(before_sha: str, current_sha: str) -> list[str]:
    if not current_sha:
        raise SystemExit("current SHA is required for push change detection")

    if before_sha and before_sha != ZERO_SHA:
        command = ["git", "diff", "--name-only", before_sha, current_sha]
    else:
        command = ["git", "show", "--name-only", "--format=", current_sha]

    result = subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE)
    return normalize_paths(result.stdout.splitlines())


def normalize_paths(paths: list[str]) -> list[str]:
    return [path.strip().replace("\\", "/") for path in paths if path.strip()]


def detect_services(paths: list[str]) -> list[str]:
    selected: set[str] = set()

    def add(*services: str) -> None:
        selected.update(services)

    for path in normalize_paths(paths):
        if path.startswith("BackEnd/spring-member-gateway/"):
            add("spring-member-gateway")
        elif path.startswith("BackEnd/spring-admin-gateway/"):
            add("spring-admin-gateway")
        elif path.startswith("BackEnd/spring-security-authorization-server/"):
            add("spring-security-authorization-server")
        elif path.startswith("BackEnd/spring-user-service/"):
            add("spring-user-service")
        elif path.startswith("BackEnd/spring-member-community-service/"):
            add("spring-member-community-service")
        elif path.startswith("BackEnd/spring-member-stock-service/"):
            add("spring-member-stock-service")
        elif path.startswith("BackEnd/spring-member-bff-service/"):
            add("spring-member-bff-service")
        elif path.startswith("BackEnd/spring-admin-bff-service/"):
            add("spring-admin-bff-service")
        elif path.startswith("BackEnd/spring-msa-common-web/"):
            add(
                "spring-security-authorization-server",
                "spring-user-service",
                "spring-member-bff-service",
                "spring-admin-bff-service",
            )
        elif path.startswith("BackEnd/spring-msa-common-kafka/"):
            add("spring-member-bff-service")
        elif path == "infra/nginx/web/member-web.conf":
            add("spring-member-web", "spring-community-web", "spring-stock-web")
        elif path == "infra/nginx/web/admin-web.conf":
            add("spring-admin-web", "spring-admin-users-web", "spring-admin-logs-web")
        elif path.startswith("FrontEnd/apps/member/"):
            if path.endswith("Dockerfile.stock") or path.startswith("FrontEnd/apps/member/src/stock/"):
                add("spring-stock-web")
            elif path.endswith("Dockerfile.community") or path.startswith("FrontEnd/apps/member/src/community/"):
                add("spring-community-web")
            elif path.endswith("Dockerfile.member"):
                add("spring-member-web")
            else:
                add("spring-member-web", "spring-community-web", "spring-stock-web")
        elif path.startswith("FrontEnd/apps/admin/"):
            if path.endswith("Dockerfile.users") or path.startswith("FrontEnd/apps/admin/src/users/"):
                add("spring-admin-users-web")
            elif path.endswith("Dockerfile.logs") or path.startswith("FrontEnd/apps/admin/src/logs/"):
                add("spring-admin-logs-web")
            elif path.endswith("Dockerfile.admin"):
                add("spring-admin-web")
            else:
                add("spring-admin-web", "spring-admin-users-web", "spring-admin-logs-web")

    return [service for service in service_order() if service in selected]


def select_services(event_name: str, deploy_target: str, changed_files: list[str]) -> tuple[list[str], list[str]]:
    services = service_order()
    known_services = set(services)

    if event_name == "workflow_dispatch":
        if deploy_target == "all":
            return services, ["all"]
        if deploy_target in known_services:
            return [deploy_target], [deploy_target]
        raise SystemExit(f"Unknown deploy target: {deploy_target}")

    selected = detect_services(changed_files)
    return selected, selected


def matrix_for(images: list[dict[str, str]], selected_services: list[str]) -> list[dict[str, str]]:
    selected = set(selected_services)
    return [item for item in images if item["service"] in selected]


def write_github_output(path: str | None, values: dict[str, str]) -> None:
    if not path:
        return

    with Path(path).open("a", encoding="utf-8") as output:
        for name, value in values.items():
            output.write(f"{name}={value}\n")


def main() -> int:
    args = parse_args()

    if args.changed_file:
        changed_files = normalize_paths(args.changed_file)
    elif args.event_name == "workflow_dispatch":
        changed_files = []
    else:
        changed_files = changed_files_from_git(args.before_sha, args.current_sha)

    selected_services, update_targets = select_services(
        args.event_name,
        args.deploy_target,
        changed_files,
    )
    backend_matrix = matrix_for(BACKEND_IMAGES, selected_services)
    frontend_matrix = matrix_for(FRONTEND_IMAGES, selected_services)

    output_values = {
        "backend_matrix": json.dumps({"include": backend_matrix}, separators=(",", ":")),
        "frontend_matrix": json.dumps({"include": frontend_matrix}, separators=(",", ":")),
        "has_backend": str(bool(backend_matrix)).lower(),
        "has_frontend": str(bool(frontend_matrix)).lower(),
        "update_targets": " ".join(update_targets),
    }
    write_github_output(args.github_output, output_values)

    if changed_files:
        print("Changed files:")
        for file_name in changed_files:
            print(f"- {file_name}")

    print(f"Event name: {args.event_name}")
    print(f"Deploy target input: {args.deploy_target}")
    print("Backend images:", ", ".join(item["service"] for item in backend_matrix) or "none")
    print("Frontend images:", ", ".join(item["service"] for item in frontend_matrix) or "none")
    print("Kubernetes update targets:", ", ".join(update_targets) or "none")

    if args.print_json:
        print(json.dumps(output_values, indent=2))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
