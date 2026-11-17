function Resolve-Java([string]$JavaHome) {
    if(!$JavaHome){$JavaHome=$env:JAVA_HOME}
    if(!$JavaHome){
        $javac=Get-Command javac -ErrorAction SilentlyContinue
        if($javac){$JavaHome=Split-Path (Split-Path $javac.Source -Parent) -Parent}
    }
    if(!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin/javac.exe'))){
        throw 'Install JDK 17+ and set JAVA_HOME or pass -JavaHome.'
    }
    return $JavaHome
}
function Resolve-Android([string]$SdkRoot) {
    if(!$SdkRoot){$SdkRoot=$env:ANDROID_SDK_ROOT}
    if(!$SdkRoot){$SdkRoot=$env:ANDROID_HOME}
    if(!$SdkRoot -and $env:LOCALAPPDATA){$SdkRoot=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
    if(!$SdkRoot -or !(Test-Path $SdkRoot)){throw 'Set ANDROID_SDK_ROOT or pass -SdkRoot.'}
    $platforms=@(Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory | ForEach-Object {
        $props=Join-Path $_.FullName 'source.properties'
        if((Test-Path $props) -and (Test-Path (Join-Path $_.FullName 'android.jar'))){
            $api=Select-String -Path $props -Pattern '^AndroidVersion.ApiLevel=(\d+)' | Select-Object -First 1
            if($api -and [int]$api.Matches[0].Groups[1].Value -ge 28){
                [PSCustomObject]@{Api=[int]$api.Matches[0].Groups[1].Value;Jar=(Join-Path $_.FullName 'android.jar')}
            }
        }
    } | Sort-Object Api -Descending)
    $tools=@(Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | ForEach-Object {
        $props=Join-Path $_.FullName 'source.properties'
        if((Test-Path $props) -and (Test-Path (Join-Path $_.FullName 'lib/d8.jar'))){
            $revision=Select-String -Path $props -Pattern '^Pkg.Revision=(\d+\.\d+\.\d+)$' | Select-Object -First 1
            if($revision){[PSCustomObject]@{Version=[version]$revision.Matches[0].Groups[1].Value;Path=$_.FullName}}
        }
    } | Sort-Object Version -Descending)
    if(!$platforms.Count -or !$tools.Count){throw 'Install an Android SDK platform API 28+ and stable Build Tools 35.0.0+.'}
    if($tools[0].Version -lt [version]'35.0.0'){throw 'Build Tools 35.0.0+ required.'}
    return [PSCustomObject]@{Root=$SdkRoot;Jar=$platforms[0].Jar;BuildTools=$tools[0].Path}
}
function Invoke-Tool([string]$Exe,[string[]]$Arguments) {
    & $Exe @Arguments
    if($LASTEXITCODE -ne 0){throw "$Exe failed: $LASTEXITCODE"}
}
