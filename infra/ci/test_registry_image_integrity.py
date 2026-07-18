from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace
from unittest import mock
import importlib.util
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name("registry_image_integrity.py")
spec = importlib.util.spec_from_file_location("registry_image_integrity", MODULE_PATH)
registry_image_integrity = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(registry_image_integrity)


DIGEST_A = "sha256:" + "a" * 64
DIGEST_B = "sha256:" + "b" * 64


class RegistryImageIntegrityTest(unittest.TestCase):
    def test_missing_image_is_allowed_only_for_not_found(self) -> None:
        with mock.patch.object(
            registry_image_integrity,
            "run_crane",
            return_value=registry_image_integrity.CommandResult(1, "", "MANIFEST_UNKNOWN: 404"),
        ):
            self.assertIsNone(
                registry_image_integrity.image_digest("registry.example/app:sha", allow_missing=True)
            )

    def test_authentication_failure_is_not_treated_as_missing(self) -> None:
        with mock.patch.object(
            registry_image_integrity,
            "run_crane",
            return_value=registry_image_integrity.CommandResult(1, "", "UNAUTHORIZED"),
        ):
            with self.assertRaises(registry_image_integrity.IntegrityError):
                registry_image_integrity.image_digest(
                    "registry.example/app:sha",
                    allow_missing=True,
                )

    def test_existing_equal_destination_is_skipped(self) -> None:
        args = SimpleNamespace(
            source="ghcr.io/example/app:source",
            destination="ecr.example/app:source",
            github_output=None,
        )
        with mock.patch.object(
            registry_image_integrity,
            "image_digest",
            side_effect=[DIGEST_A, DIGEST_A],
        ), mock.patch.object(registry_image_integrity, "run_crane") as crane:
            self.assertEqual(registry_image_integrity.promote_command(args), 0)
            crane.assert_not_called()

    def test_existing_different_destination_fails(self) -> None:
        args = SimpleNamespace(
            source="ghcr.io/example/app:source",
            destination="ecr.example/app:source",
            github_output=None,
        )
        with mock.patch.object(
            registry_image_integrity,
            "image_digest",
            side_effect=[DIGEST_A, DIGEST_B],
        ):
            with self.assertRaises(registry_image_integrity.IntegrityError):
                registry_image_integrity.promote_command(args)

    def test_missing_destination_is_copied_by_source_digest_and_verified(self) -> None:
        args = SimpleNamespace(
            source="ghcr.io/example/app:source",
            destination="ecr.example/app:source",
            github_output=None,
        )
        with mock.patch.object(
            registry_image_integrity,
            "image_digest",
            side_effect=[DIGEST_A, None, DIGEST_A],
        ), mock.patch.object(
            registry_image_integrity,
            "run_crane",
            return_value=registry_image_integrity.CommandResult(0, "", ""),
        ) as crane:
            self.assertEqual(registry_image_integrity.promote_command(args), 0)
            crane.assert_called_once_with(
                ["copy", f"ghcr.io/example/app@{DIGEST_A}", "ecr.example/app:source"]
            )

    def test_github_output_contains_immutable_destination_reference(self) -> None:
        args = SimpleNamespace(
            source="ghcr.io/example/app:source",
            destination="ecr.example/app:source",
            github_output=None,
        )
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "github-output.txt"
            args.github_output = str(output_path)
            with mock.patch.object(
                registry_image_integrity,
                "image_digest",
                side_effect=[DIGEST_A, DIGEST_A],
            ):
                registry_image_integrity.promote_command(args)

            output = output_path.read_text(encoding="utf-8")
            self.assertIn(f"destination_image=ecr.example/app@{DIGEST_A}", output)


if __name__ == "__main__":
    unittest.main()
