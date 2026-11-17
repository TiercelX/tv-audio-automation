param([switch]$Device,[switch]$Ui,[string]$Serial='', [string]$JavaHome='', [string]$SdkRoot='', [string]$AdbPath='', [string]$KeyStore='.keys/probe.jks')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$jdk=Resolve-Java $JavaHome
New-Item -ItemType Directory -Force build/test | Out-Null
Invoke-Tool "$jdk/bin/javac.exe" @('-encoding','UTF-8','-d','build/test','src/io/tiercel/a7volumeprobe/Schedule.java','tests/ScheduleTest.java')
Invoke-Tool "$jdk/bin/java.exe" @('-cp','build/test','ScheduleTest')
if(!$Device -and !$Ui){return}
$sdk=Resolve-Android $SdkRoot
$bt=$sdk.BuildTools
$androidJar=$sdk.Jar
& (Join-Path $PSScriptRoot 'build.ps1') -JavaHome $jdk -SdkRoot $sdk.Root -KeyStore $KeyStore
New-Item -ItemType Directory -Force build/device-classes,build/device-dex | Out-Null
Invoke-Tool "$jdk/bin/javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',"$androidJar;build/classes",'-d','build/device-classes','tests/device/DeviceSmokeTest.java','tests/device/UiSmokeTest.java')
$classes=@(Get-ChildItem build/device-classes -Recurse -Filter '*.class' | ForEach-Object {$_.FullName})
Invoke-Tool "$jdk/bin/java.exe" (@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--classpath','build/classes','--min-api','25','--output','build/device-dex')+$classes)
Invoke-Tool "$bt/aapt2.exe" @('link','--manifest','tests/device/AndroidManifest.xml','-I',$androidJar,'-o','build/device-base.apk')
Invoke-Tool "$jdk/bin/jar.exe" @('uf','build/device-base.apk','-C','build/device-dex','classes.dex')
Invoke-Tool "$bt/zipalign.exe" @('-f','4','build/device-base.apk','build/device-aligned.apk')
Invoke-Tool "$jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'sign','--ks',$KeyStore,'--ks-pass','pass:android','--out','build/device-tests.apk','build/device-aligned.apk')
if(!$AdbPath){$AdbPath=Join-Path $sdk.Root 'platform-tools/adb.exe'}
if(!(Test-Path $AdbPath)){
    $command=Get-Command adb -ErrorAction SilentlyContinue
    if(!$command){throw 'Install SDK Platform Tools, put adb on PATH, or pass -AdbPath.'}
    $AdbPath=$command.Source
}
if(!$Serial){
    $connected=@(& $AdbPath devices | ForEach-Object {if($_ -match '^(\S+)\s+device$'){$Matches[1]}})
    if($connected.Count -ne 1){throw 'Connect exactly one authorized device or pass -Serial'}
    $Serial=$connected[0]
}
Invoke-Tool $AdbPath @('-s',$Serial,'install','--no-incremental','-r','dist/A7VolumeScheduler.apk')
Invoke-Tool $AdbPath @('-s',$Serial,'install','--no-incremental','-r','build/device-tests.apk')
function Invoke-Instrumentation([string]$Runner,[string]$Success){
    $output=& $AdbPath -s $Serial shell am instrument -w "io.tiercel.a7volumeprobe.test/io.tiercel.a7volumeprobe.$Runner" 2>&1
    $code=$LASTEXITCODE
    $output | ForEach-Object {Write-Output $_}
    $output | Set-Content -LiteralPath "build/$Runner-result.txt" -Encoding utf8
    if($code -ne 0 -or ($output -join "`n") -notmatch [regex]::Escape($Success)){throw "Instrumentation $Runner failed"}
}
if($Device){Invoke-Instrumentation 'DeviceSmokeTest' 'DEVICE TESTS PASSED'}
if($Ui){Invoke-Instrumentation 'UiSmokeTest' 'UI TESTS PASSED'}
