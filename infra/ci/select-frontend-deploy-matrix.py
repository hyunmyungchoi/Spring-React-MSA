from __future__ import annotations

import json
import os
from pathlib import Path


FRONTEND_DEPLOYMENTS: tuple[dict[str, object], ...] = (
    {
        "service": "spring-member-web",
        "workspace": "member",
        "build_script": "build:prod",
        "output_dir": "FrontEnd/apps/member/dist",
        "entry_document": "index.html",
        "bucket_key": "member",
        "distribution_group": "member",
        "invalidation_paths": ["/", "/index.html", "/auth*", "/chat*", "/assets/*"],
    },
    {
        "service": "spring-community-web",
        "workspace": "member",
        "build_script": "build:community",
        "output_dir": "FrontEnd/apps/member/dist/community",
        "entry_document": "community.html",
        "bucket_key": "community",
        "distribution_group": "member",
        "invalidation_paths": ["/community", "/community/*"],
    },
    {
        "service": "spring-stock-web",
        "workspace": "member",
        "build_script": "build:stock",
        "output_dir": "FrontEnd/apps/member/dist/stock",
        "entry_document": "stock.html",
        "bucket_key": "stock",
        "distribution_group": "member",
        "invalidation_paths": ["/stock", "/stock/*"],
    },
    {
        "service": "spring-admin-web",
        "workspace": "admin",
        "build_script": "build:prod",
        "output_dir": "FrontEnd/apps/admin/dist",
        "entry_document": "index.html",
        "bucket_key": "admin",
        "distribution_group": "admin",
        "invalidation_paths": ["/", "/index.html", "/auth*", "/assets/*"],
    },
    {
        "service": "spring-admin-users-web",
        "workspace": "admin",
        "build_script": "build:users",
        "output_dir": "FrontEnd/apps/admin/dist/users",
        "entry_document": "users.html",
        "bucket_key": "admin-users",
        "distribution_group": "admin",
        "invalidation_paths": ["/manage/users", "/manage/users/*"],
    },
    {
        "service": "spring-admin-logs-web",
        "workspace": "admin",
        "build_script": "build:logs",
        "output_dir": "FrontEnd/apps/admin/dist/logs",
        "entry_document": "logs.html",
        "bucket_key": "admin-logs",
        "distribution_group": "admin",
        "invalidation_paths": ["/manage/logs", "/manage/logs/*"],
    },
)


TARGET_GROUPS = {
    "all": tuple(item["service"] for item in FRONTEND_DEPLOYMENTS),
    "all-member": tuple(
        item["service"]
        for item in FRONTEND_DEPLOYMENTS
        if item["distribution_group"] == "member"
    ),
    "all-admin": tuple(
        item["service"]
        for item in FRONTEND_DEPLOYMENTS
        if item["distribution_group"] == "admin"
    ),
}


def select_deployments(target: str) -> list[dict[str, object]]:
    service_names = TARGET_GROUPS.get(target, (target,))
    selected = [
        dict(item)
        for item in FRONTEND_DEPLOYMENTS
        if item["service"] in service_names
    ]
    if not selected or len(selected) != len(service_names):
        raise ValueError(f"Unsupported frontend deployment target: {target}")
    return selected


def matrix_json(target: str) -> str:
    return json.dumps(
        {"include": select_deployments(target)},
        separators=(",", ":"),
    )


def write_github_output(name: str, value: str) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        return
    with Path(output_path).open("a", encoding="utf-8") as output:
        output.write(f"{name}={value}\n")


def main() -> None:
    target = os.environ.get("DEPLOY_TARGET", "all")
    matrix = matrix_json(target)
    write_github_output("matrix", matrix)
    print(matrix)


if __name__ == "__main__":
    main()
