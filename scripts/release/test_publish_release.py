"""Publisher regressions; all GitHub operations use an in-memory fake."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import publish_release as publisher


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name)
        self.commit = 'a' * 40
        self.notes = 'Users with v1.0.0 must uninstall it before installing v1.1.0.\n'
        (self.folder / publisher.APK).write_bytes(b'public APK fixture')
        apk_hash = publisher.digest(self.folder / publisher.APK)
        (self.folder / (publisher.APK + '.sha256')).write_text(f'{apk_hash}  {publisher.APK}\n')
        (self.folder / 'verification.json').write_text(json.dumps({'sha256': apk_hash, 'source_commit': self.commit}))
        (self.folder / 'release-notes.md').write_text(self.notes)
        self.release = None
        self.uploaded = {}
        self.calls = []
        self.tag = publisher.RELEASE_COMMIT
        self.changed_files = ['scripts/release/publish_release.py']

    def draft(self):
        return {'id': 7, 'tag_name': publisher.TAG, 'name': publisher.TITLE,
                'body': self.notes, 'draft': True, 'prerelease': False, 'assets': [],
                'html_url': f'https://github.com/{publisher.REPO}/releases/tag/{publisher.TAG}'}

    def add_asset(self, name):
        self.uploaded[name] = (self.folder / name).read_bytes()
        self.release['assets'].append({'name': name, 'state': 'uploaded',
            'browser_download_url': f'https://github.com/{publisher.REPO}/releases/download/{publisher.TAG}/{name}'})

    def api(self, path, **kwargs):
        if path == '':
            return {'private': False}
        if path == 'branches/main':
            return {'commit': {'sha': self.commit}}
        if path.startswith('actions/runs?'):
            return {'workflow_runs': [{'head_sha': self.commit, 'name': 'Android checks',
                                       'id': 1, 'conclusion': 'success'}]}
        if path == f'git/ref/tags/{publisher.TAG}':
            return {'object': {'type': 'commit', 'sha': self.tag}}
        if path.startswith('compare/'):
            return {'merge_base_commit': {'sha': publisher.RELEASE_COMMIT}, 'status': 'ahead',
                    'files': [{'filename': f} for f in self.changed_files]}
        if path.startswith('releases?'):
            return [self.release] if self.release else []
        if path == 'releases/7':
            return self.release
        raise AssertionError('Unexpected API operation: ' + path)

    def gh(self, *args, **kwargs):
        self.calls.append(args)
        command = args[:2]
        if command == ('release', 'create'):
            self.assertIn('--verify-tag', args)
            self.assertNotIn('--target', args)
            self.assertIsNone(self.release)
            self.release = self.draft()
        elif command == ('release', 'upload'):
            self.assertNotIn('--clobber', args)
            name = Path(args[3]).name
            self.assertNotIn(name, self.uploaded)
            self.add_asset(name)
        elif command == ('release', 'download'):
            folder = Path(args[args.index('--dir') + 1])
            for index, argument in enumerate(args):
                if argument == '--pattern':
                    name = args[index + 1]
                    (folder / name).write_bytes(self.uploaded[name])
        elif command == ('release', 'edit'):
            self.release['draft'] = False
        else:
            raise AssertionError('Unexpected mutation: ' + repr(args))
        return ''

    def run_publisher(self):
        with (patch.object(publisher, 'api', side_effect=self.api),
              patch.object(publisher, 'gh', side_effect=self.gh),
              patch.object(publisher.urllib.request, 'urlopen',
                           side_effect=lambda url, **kw: io.BytesIO(self.uploaded[url.rsplit('/', 1)[1]])),
              patch.dict(os.environ, {'GITHUB_SHA': self.commit, 'GITHUB_REPOSITORY': publisher.REPO,
                                      'GITHUB_STEP_SUMMARY': ''}),
              patch('sys.argv', ['publish_release.py', '--directory', str(self.folder)]),
              contextlib.redirect_stdout(io.StringIO())):
            publisher.main()

    def assert_no_mutations(self):
        self.assertTrue(all(call[:2] == ('release', 'download') for call in self.calls))

    def test_existing_tag_without_release_and_read_only_retry(self):
        self.run_publisher()
        self.assertFalse(self.release['draft'])
        self.assertEqual(set(self.uploaded), {publisher.APK, publisher.APK + '.sha256'})
        self.assertEqual(self.tag, publisher.RELEASE_COMMIT)
        self.calls.clear()
        self.run_publisher()
        self.assert_no_mutations()

    def test_resume_matching_partial_draft_without_replacing_assets(self):
        self.release = self.draft()
        self.add_asset(publisher.APK)
        self.run_publisher()
        uploads = [call for call in self.calls if call[:2] == ('release', 'upload')]
        self.assertEqual(len(uploads), 1)
        self.assertEqual(Path(uploads[0][3]).name, publisher.APK + '.sha256')

    def test_wrong_tag_is_rejected_before_any_write(self):
        self.tag = 'b' * 40
        with self.assertRaisesRegex(RuntimeError, 'tag must point'):
            self.run_publisher()
        self.assert_no_mutations()

    def test_application_change_after_tag_is_rejected(self):
        self.changed_files = ['app/build.gradle.kts']
        with self.assertRaisesRegex(RuntimeError, 'outside the publisher fix'):
            self.run_publisher()
        self.assert_no_mutations()

    def test_existing_release_metadata_is_never_overwritten(self):
        self.release = self.draft()
        self.release['body'] = 'Different release notes'
        with self.assertRaisesRegex(RuntimeError, 'will not be overwritten'):
            self.run_publisher()
        self.assert_no_mutations()

    def test_existing_asset_mismatch_is_never_overwritten(self):
        self.release = self.draft()
        self.add_asset(publisher.APK)
        self.uploaded[publisher.APK] = b'different bytes'
        with self.assertRaisesRegex(RuntimeError, 'differs from the verified build'):
            self.run_publisher()
        self.assert_no_mutations()

    def test_cli_failure_identifies_operation_and_redacts_secrets(self):
        token = 'test-token-do-not-log'
        failure = subprocess.CompletedProcess([], 1, '', 'gh: HTTP 403 ' + token + '\nAuthorization: Bearer private')
        with (patch.dict(os.environ, {'GH_TOKEN': token, 'GH_DEBUG': 'api'}),
              patch.object(publisher.subprocess, 'run', return_value=failure) as command,
              contextlib.redirect_stdout(io.StringIO())):
            with self.assertRaises(RuntimeError) as error:
                publisher.gh('release', 'create', publisher.TAG, operation='create draft (POST /releases)')
            self.assertIn('POST /releases', str(error.exception))
            self.assertIn('HTTP 403', str(error.exception))
            self.assertNotIn(token, str(error.exception))
            self.assertNotIn('Bearer private', str(error.exception))
            self.assertNotIn('GH_DEBUG', command.call_args.kwargs['env'])


if __name__ == '__main__':
    unittest.main()
