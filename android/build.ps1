param([string]$JavaHome='', [string]$SdkRoot='', [string]$KeyStore='.keys/probe.jks')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$jdk=Resolve-Java $JavaHome
$sdk=Resolve-Android $SdkRoot
$bt=$sdk.BuildTools
$androidJar=$sdk.Jar
New-Item -ItemType Directory -Force build,build/classes,build/dex,dist | Out-Null
$sources=@(Get-ChildItem src -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
Invoke-Tool "$jdk/bin/javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$androidJar,'-d','build/classes')+$sources)
$classes=@(Get-ChildItem build/classes -Filter '*.class' -Recurse | ForEach-Object {$_.FullName})
Invoke-Tool "$jdk/bin/java.exe" (@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','25','--output','build/dex')+$classes)
Invoke-Tool "$bt/aapt2.exe" @('link','--manifest','AndroidManifest.xml','-I',$androidJar,'--min-sdk-version','25','--target-sdk-version','25','-o','build/base.apk')
Invoke-Tool "$jdk/bin/jar.exe" @('uf','build/base.apk','-C','build/dex','classes.dex')
Invoke-Tool "$bt/zipalign.exe" @('-f','4','build/base.apk','build/aligned.apk')
if(!(Test-Path $KeyStore)){
    New-Item -ItemType Directory -Force (Split-Path $KeyStore -Parent) | Out-Null
    Invoke-Tool "$jdk/bin/keytool.exe" @('-genkeypair','-keystore',$KeyStore,'-storepass','android','-keypass','android','-alias','probe','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Local media volume scheduler')
}
Invoke-Tool "$jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'sign','--ks',$KeyStore,'--ks-pass','pass:android','--key-pass','pass:android','--out','dist/A7VolumeScheduler.apk','build/aligned.apk')
Invoke-Tool "$jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'verify','--verbose','dist/A7VolumeScheduler.apk')
