#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys
from typing import NamedTuple, Sequence


DIGEST_PATTERN = re.compile(r"sha256:[0-9a-f]{64}")
NOT_FOUND_MARKERS = (
    "manifest_unknown",
    "name_unknown",
    "not_found",
    "not found",
    "404",
)


class IntegrityError(RuntimeError):
    pass


class CommandResult(NamedTuple):
    returncode: int
    stdout: str
    stderr: str


def run_crane(arguments: Sequence[str]) -> CommandResult:
    completed = subprocess.run(
        ["crane", *arguments],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return CommandResult(completed.returncode, completed.stdout, completed.stderr)


def is_not_found(message: str) -> bool:
    normalized = message.lower()
    return any(marker in normalized for marker in NOT_FOUND_MARKERS)


def validate_digest(value: str, image: str) -> str:
    digest = value.strip()
    if not DIGEST_PATTERN.fullmatch(digest):
        raise IntegrityError(f"Registry returned an invalid digest for {image}: {digest!r}")
    return digest


def image_digest(image: str, *, allow_missing: bool = False) -> str | None:
    result = run_crane(["digest", image])
    if result.returncode == 0:
        return validate_digest(result.stdout, image)
    if allow_missing and is_not_found(f"{result.stdout}\n{result.stderr}"):
        return None
    raise IntegrityError(f"Unable to resolve {image}: {result.stderr.strip() or result.stdout.strip()}")


def digest_reference(tagged_image: str, digest: str) -> str:
    repository, separator, tag = tagged_image.rpartition(":")
    if not separator or "/" not in repository or not tag:
        raise IntegrityError(f"Expected a registry image with an explicit tag: {tagged_image}")
    return f"{repository}@{digest}"


def write_github_output(path: str | None, values: dict[str, str]) -> None:
    if not path:
        return
    with Path(path).open("a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def inspect_command(args: argparse.Namespace) -> int:
    digest = image_digest(args.image, allow_missing=args.allow_missing)
    values = {
        "exists": str(digest is not None).lower(),
        "digest": digest or "",
    }
    write_github_output(args.github_output, values)
    print(f"exists={values['exists']}")
    if digest:
        print(f"digest={digest}")
    return 0


def promote_command(args: argparse.Namespace) -> int:
    source_digest = image_digest(args.source)
    assert source_digest is not None

    destination_digest = image_digest(args.destination, allow_missing=True)
    if destination_digest is not None:
        if destination_digest != source_digest:
            raise IntegrityError(
                "Immutable destination tag already exists with a different digest: "
                f"source={source_digest}, destination={destination_digest}"
            )
        action = "skipped"
    else:
        immutable_source = digest_reference(args.source, source_digest)
        copy_result = run_crane(["copy", immutable_source, args.destination])
        if copy_result.returncode != 0:
            raise IntegrityError(
                f"Registry copy failed: {copy_result.stderr.strip() or copy_result.stdout.strip()}"
            )
        destination_digest = image_digest(args.destination)
        if destination_digest != source_digest:
            raise IntegrityError(
                "Digest changed during registry promotion: "
                f"source={source_digest}, destination={destination_digest}"
            )
        action = "promoted"

    values = {
        "action": action,
        "source_digest": source_digest,
        "destination_digest": destination_digest,
        "destination_image": digest_reference(args.destination, destination_digest),
    }
    write_github_output(args.github_output, values)
    for key, value in values.items():
        print(f"{key}={value}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inspect and promote immutable OCI images with crane.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect_parser = subparsers.add_parser("inspect")
    inspect_parser.add_argument("--image", required=True)
    inspect_parser.add_argument("--allow-missing", action="store_true")
    inspect_parser.add_argument("--github-output")
    inspect_parser.set_defaults(handler=inspect_command)

    promote_parser = subparsers.add_parser("promote")
    promote_parser.add_argument("--source", required=True)
    promote_parser.add_argument("--destination", required=True)
    promote_parser.add_argument("--github-output")
    promote_parser.set_defaults(handler=promote_command)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        return args.handler(args)
    except IntegrityError as error:
        print(f"Image integrity error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
