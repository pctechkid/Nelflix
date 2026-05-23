param(
    [Parameter(Mandatory = $true)]
    [string]$Tag,

    [Parameter(Mandatory = $true)]
    [string]$Title,

    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$Repo = "pctechkid/Nelflix"
)

$notes = @"
Nelflix debug APK update.

Changes:
- Replace these bullets with the real release notes.
"@

gh release create $Tag $ApkPath --repo $Repo --title $Title --notes $notes
