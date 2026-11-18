import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[1]


def workflow_step(name):
    workflow = (PROJECT_ROOT / '.github/workflows/deploy-release.yml').read_text(encoding='utf-8')
    marker = '      - name: ' + name + '\n'
    if marker not in workflow:
        raise AssertionError('The release build must prepare its pinned Grass Slabs dependency: ' + name)
    section = workflow.split(marker, 1)[1].split('\n      - name:', 1)[0]
    return section


def shell_body(section):
    lines = section.split('        run: |\n', 1)[1].splitlines()
    return '\n'.join(line[10:] if line.startswith('          ') else line for line in lines) + '\n'


def bash_executable():
    if os.name != 'nt':
        executable = shutil.which('bash')
        if executable:
            return executable
    else:
        git = shutil.which('git')
        candidates = []
        if git:
            candidates.append(Path(git).resolve().parent.parent / 'bin/bash.exe')
        candidates.append(Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'Git/bin/bash.exe')
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    raise AssertionError('Bash is required for release workflow checks; use Git Bash on Windows')


class GrassSlabsReleaseWorkflowTest(unittest.TestCase):
    def test_dependency_is_built_before_the_release_checks(self):
        workflow = (PROJECT_ROOT / '.github/workflows/deploy-release.yml').read_text(encoding='utf-8')
        for name in ['Read coordinated Grass Slabs source pin', 'Check out pinned Grass Slabs source',
                     'Build pinned optional Grass Slabs dependency']:
            workflow_step(name)
            self.assertLess(workflow.index('      - name: ' + name),
                            workflow.index('      - name: Build, test, and audit once'))
        checkout = workflow_step('Check out pinned Grass Slabs source')
        self.assertIn('repository: MinecraftModDevelopmentMods/SkysGrassSlabs', checkout)
        self.assertIn('ref: ${{ steps.grass.outputs.commit }}', checkout)

    def test_the_actual_shell_uses_the_pinned_commit_version_and_output_jar(self):
        read_pin = shell_body(workflow_step('Read coordinated Grass Slabs source pin'))
        build = shell_body(workflow_step('Build pinned optional Grass Slabs dependency'))
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            (workspace / 'gradle').mkdir()
            commit = '0123456789abcdef0123456789abcdef01234567'
            version = '1.2.1.2602001'
            (workspace / 'gradle/grass-slabs-release.properties').write_text(
                'source.commit=' + commit + '\nversion=' + version + '\n', encoding='utf-8', newline='\n')
            dependency = workspace / '.gradle/qualification-sources/grass-slabs'
            dependency.mkdir(parents=True)
            (dependency / 'gradlew').write_text(
                '#!/usr/bin/env bash\nset -euo pipefail\nprintf "%s\\n" "$@" > "$CAPTURE_FILE"\n',
                encoding='utf-8', newline='\n')
            environment = dict(os.environ, GITHUB_WORKSPACE=workspace.as_posix(),
                               GITHUB_OUTPUT=(workspace / 'outputs').as_posix(),
                               GITHUB_ENV=(workspace / 'environment').as_posix(),
                               JAVA_HOME='/qualified-java-25', CAPTURE_FILE=(workspace / 'arguments').as_posix())
            subprocess.run([bash_executable(), '-c', read_pin + build], cwd=workspace,
                           env=environment, check=True, capture_output=True, text=True)
            self.assertEqual('commit=' + commit, (workspace / 'outputs').read_text().strip())
            arguments = (workspace / 'arguments').read_text().splitlines()
            self.assertEqual('-p', arguments[0])
            self.assertEqual(dependency.as_posix(), arguments[1])
            self.assertIn('assemble', arguments)
            self.assertIn('-Dorg.gradle.java.installations.paths=/qualified-java-25', arguments)
            expected_jar = (dependency / ('build/libs/SkysGrassSlabs-' + version + '.jar')).as_posix()
            self.assertEqual('ORG_GRADLE_PROJECT_grassSlabsMainJar=' + expected_jar,
                             (workspace / 'environment').read_text().strip())

    def test_invalid_source_commit_is_rejected(self):
        read_pin = shell_body(workflow_step('Read coordinated Grass Slabs source pin'))
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            (workspace / 'gradle').mkdir()
            (workspace / 'gradle/grass-slabs-release.properties').write_text(
                'source.commit=not-a-commit\nversion=1.2.1.2602001\n', encoding='utf-8', newline='\n')
            environment = dict(os.environ, GITHUB_OUTPUT=(workspace / 'outputs').as_posix())
            result = subprocess.run([bash_executable(), '-c', read_pin], cwd=workspace,
                                    env=environment, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse((workspace / 'outputs').exists())


if __name__ == '__main__':
    unittest.main()
