from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("update-k8s-image-tags.py")
spec = importlib.util.spec_from_file_location("update_k8s_image_tags", MODULE_PATH)
update_k8s_image_tags = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(update_k8s_image_tags)


class UpdateK8sImageTagsTest(unittest.TestCase):
    def test_digest_reference_uses_at_separator(self) -> None:
        digest = "sha256:" + "a" * 64
        self.assertEqual(
            update_k8s_image_tags.image_reference(
                "ghcr.io",
                "hyunmyungchoi",
                "spring-user-service",
                None,
                digest,
            ),
            f"ghcr.io/hyunmyungchoi/spring-user-service@{digest}",
        )

    def test_invalid_digest_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            update_k8s_image_tags.image_reference(
                "ghcr.io",
                "hyunmyungchoi",
                "spring-user-service",
                None,
                "sha256:not-a-digest",
            )

    def test_full_git_sha_tag_remains_supported_for_manual_rollback(self) -> None:
        sha = "a" * 40
        self.assertEqual(
            update_k8s_image_tags.image_reference(
                "ghcr.io",
                "hyunmyungchoi",
                "spring-user-service",
                sha,
                None,
            ),
            f"ghcr.io/hyunmyungchoi/spring-user-service:{sha}",
        )


if __name__ == "__main__":
    unittest.main()
