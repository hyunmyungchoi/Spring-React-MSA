#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


MANIFESTS: dict[str, list[str]] = {
    "10-user-service.yaml": ["spring-user-service"],
    "11-community-service.yaml": ["spring-member-community-service"],
    "12-stock-service.yaml": ["spring-member-stock-service"],
    "13-auth-server.yaml": ["spring-security-authorization-server"],
    "20-member-bff-service.yaml": ["spring-member-bff-service"],
    "21-admin-bff-service.yaml": ["spring-admin-bff-service"],
    "30-member-gateway.yaml": ["spring-member-gateway"],
    "31-admin-gateway.yaml": ["spring-admin-gateway"],
    "40-web.yaml": [
        "spring-member-web",
        "spring-community-web",
        "spring-stock-web",
        "spring-admin-web",
        "spring-admin-users-web",
        "spring-admin-logs-web",
    ],
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Update Spring MSA Kubernetes image tags.")
    image_version = parser.add_mutually_exclusive_group(required=True)
    image_version.add_argument("--sha", help="Image tag to write, usually github.sha.")
    image_version.add_argument("--digest", help="Immutable OCI digest to write as sha256:<64 hex>.")
    parser.add_argument("--target", required=True, help="Deploy target name or 'all'.")
    parser.add_argument("--manifest-dir", default="infra/k8s/spring-msa")
    parser.add_argument("--registry", default="ghcr.io")
    parser.add_argument("--image-owner", default="hyunmyungchoi")
    return parser.parse_args()


def known_images() -> set[str]:
    return {image for images in MANIFESTS.values() for image in images}


def selected_images(target: str, image_names: list[str]) -> list[str]:
    if target == "all":
        return image_names
    if target in image_names:
        return [target]
    return []


def update_image(text: str, image_name: str, image_ref: str) -> tuple[str, int]:
    pattern = re.compile(rf"(- name:\s*{re.escape(image_name)}\r?\n\s*image:\s*)[^\s]+")
    return pattern.subn(lambda match: f"{match.group(1)}{image_ref}", text)


def image_reference(registry: str, image_owner: str, image_name: str, sha: str | None, digest: str | None) -> str:
    repository = f"{registry}/{image_owner}/{image_name}"
    if digest is not None:
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise ValueError(f"Invalid OCI digest: {digest}")
        return f"{repository}@{digest}"

    if sha is None or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError(f"Invalid full Git SHA: {sha}")
    return f"{repository}:{sha}"


def main() -> int:
    args = parse_args()
    manifest_dir = Path(args.manifest_dir)
    target = args.target

    images = known_images()
    if target != "all" and target not in images:
        raise SystemExit(f"Unknown deploy target: {target}")

    updates = 0
    for file_name, image_names in MANIFESTS.items():
        targets = selected_images(target, image_names)
        if not targets:
            continue

        path = manifest_dir / file_name
        text = path.read_text(encoding="utf-8")

        for image_name in targets:
            try:
                image_ref = image_reference(
                    args.registry,
                    args.image_owner,
                    image_name,
                    args.sha,
                    args.digest,
                )
            except ValueError as error:
                raise SystemExit(str(error)) from error
            text, count = update_image(text, image_name, image_ref)

            if count != 1:
                raise SystemExit(f"Expected one image line for {image_name} in {path}, found {count}")

            updates += count

        path.write_text(text, encoding="utf-8")

    if updates == 0:
        raise SystemExit(f"No Kubernetes image tag updates were made for target: {target}")

    print(f"Updated {updates} Kubernetes image tag(s) for target: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
