[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

$exe = (Get-Item "extracted/*/*.exe").FullName
Write-Output "Exe: $exe"

$proc = Start-Process -FilePath $exe -PassThru
Start-Sleep -Milliseconds 2000

$mainHwnd = $proc.MainWindowHandle
Write-Output "MainWindowHandle: $mainHwnd"

$element = [System.Windows.Automation.AutomationElement]::FromHandle($mainHwnd)
if ($element -ne $null) {
    $cond = [System.Windows.Automation.Condition]::TrueCondition
    $children = $element.FindAll([System.Windows.Automation.TreeScope]::Descendants, $cond)
    Write-Output "Found $($children.Count) elements"
    foreach ($c in $children) {
        $type = $c.Current.ControlType.ProgrammaticName
        $name = $c.Current.Name
        $id = $c.Current.AutomationId
        $cls = $c.Current.ClassName
        Write-Output "$type | Name: [$name] | ID: [$id] | Class: [$cls]"
    }
} else {
    Write-Output "Element is null"
}
$proc.Kill()
