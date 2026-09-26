from pathlib import Path
import subprocess

expected={
 'app/build.gradle.kts':'73380e9f3542084a094644f299345c70894be25a',
 'README.md':'f1f04b5ffeaedaa53cab49e0b33011c0ec2221fc',
 'app/src/androidTest/java/tw/techtarian/browser/CloseAllTabsTest.kt':'18c395848e050c10dd3a37ef654cc5ae9c351b5a',
 'release-smoke/src/androidTest/java/tw/techtarian/browser/smoketests/OptimizedReleaseTest.java':'198d40951b50225d8fcb03778df6a50bb873d2c2'
}
for path,sha in expected.items():
    assert subprocess.check_output(['git','hash-object',path],text=True).strip()==sha,path

def replace(path,old,new):
    p=Path(path);text=p.read_text();assert text.count(old)==1,(path,old);p.write_text(text.replace(old,new))

replace('app/build.gradle.kts','versionCode = 34','versionCode = 35')
replace('app/build.gradle.kts','versionName = "1.10.1"','versionName = "1.10.2"')
replace('README.md','**1.10.1（versionCode 34）**','**1.10.2（versionCode 35）**')
replace('README.md','[1.10.1 更新說明](docs/releases/1.10.1.md)','[1.10.2 更新說明](docs/releases/1.10.2.md)')
replace('release-smoke/src/androidTest/java/tw/techtarian/browser/smoketests/OptimizedReleaseTest.java',
        'assertEquals(34,target().getPackageManager().getPackageInfo(APP,0).getLongVersionCode());',
        'assertEquals(35,target().getPackageManager().getPackageInfo(APP,0).getLongVersionCode());')
tests='app/src/androidTest/java/tw/techtarian/browser/CloseAllTabsTest.kt'
replace(tests,'import android.os.Process\n','import android.os.Process\nimport androidx.activity.compose.setContent\nimport androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.width\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.setValue\nimport androidx.compose.ui.unit.dp\n')
marker='\n\n}\n\n/** Invoke each method in a separate process; never operate on the production package. */'
replace(tests,marker,'\n'+Path('.ci/menu-layout-tests.inc.kt').read_text()+marker)
subprocess.run(['git','diff','--check'],check=True)
