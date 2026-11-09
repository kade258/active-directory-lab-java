#Requires -Version 5.1
[CmdletBinding()]
param([switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Force '.build/classes' | Out-Null
    $sources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 21 -encoding UTF-8 -d .build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item src/main/resources/* .build/classes -Recurse -Force
    & jar --create --file .build/active-directory-lab.jar --main-class lab.Main -C .build/classes .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
    if ($Test) {
        New-Item -ItemType Directory -Force '.build/test-classes' | Out-Null
        $tests = @(Get-ChildItem src/test/java -Recurse -Filter '*.java' | ForEach-Object FullName)
        & javac --release 21 -encoding UTF-8 -cp .build/classes -d .build/test-classes $tests
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        & java -cp '.build/classes;.build/test-classes' lab.LabTests
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
    }
    Write-Host 'Built .build/active-directory-lab.jar'
} finally { Pop-Location }
