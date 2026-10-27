# PowerShell script for end-to-end functional testing of expense-settler application
function Invoke-Request($method, $url, $body = $null) {
    $headers = @{ "Content-Type" = "application/json; charset=UTF-8" }
    try {
        $params = @{ Uri = $url; Method = $method; Headers = $headers; UseBasicParsing = $true }
        if ($body) { $params.Body = $body }
        $resp = Invoke-WebRequest @params -ErrorAction Stop
        return @{ Status = $resp.StatusCode; Body = $resp.Content }
    } catch [System.Net.WebException] {
        $webResp = $_.Exception.Response
        $status = $webResp.StatusCode.value__
        $stream = $webResp.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $content = $reader.ReadToEnd()
        return @{ Status = $status; Body = $content }
    }
}

Write-Host "=== Test 1: Add members ==="
$members = @("Rick","Aman","Riya")
$memberIds = @{}
foreach ($name in $members) {
    $body = @{ name = $name } | ConvertTo-Json -Compress
    $result = Invoke-Request -method POST -url http://localhost:8080/api/members -body $body
    Write-Host "POST /api/members {name:`"$name`"} => Status $($result.Status) Body $($result.Body)"
    if ($result.Status -eq 201) {
        $obj = $result.Body | ConvertFrom-Json
        $memberIds[$name] = $obj.id
    }
}
Write-Host "=== Verify GET /api/members ==="
$getResult = Invoke-Request -method GET -url http://localhost:8080/api/members
Write-Host "GET /api/members => Status $($getResult.Status) Body $($getResult.Body)"

Write-Host "=== Test 2: Add expenses ==="
$expenses = @(
    @{ description = "Dinner"; amount = 900; payerId = $memberIds["Rick"] },
    @{ description = "Cab"; amount = 300; payerId = $memberIds["Aman"] }
)
foreach ($exp in $expenses) {
    $body = $exp | ConvertTo-Json -Compress
    $result = Invoke-Request -method POST -url http://localhost:8080/api/expenses -body $body
    Write-Host "POST /api/expenses $body => Status $($result.Status) Body $($result.Body)"
}
Write-Host "=== Verify GET /api/expenses ==="
$expGet = Invoke-Request -method GET -url http://localhost:8080/api/expenses
Write-Host "GET /api/expenses => Status $($expGet.Status) Body $($expGet.Body)"

Write-Host "=== Test 3: Summary ==="
$summary = Invoke-Request -method GET -url http://localhost:8080/api/summary
Write-Host "GET /api/summary => Status $($summary.Status) Body $($summary.Body)"
Write-Host "=== Test 3: Settlements ==="
$settlements = Invoke-Request -method GET -url http://localhost:8080/api/settlements
Write-Host "GET /api/settlements => Status $($settlements.Status) Body $($settlements.Body)"

Write-Host "=== Validation Tests ==="
# Blank name
$blank = Invoke-Request -method POST -url http://localhost:8080/api/members -body '{"name":""}'
Write-Host "POST blank name => Status $($blank.Status) Body $($blank.Body)"
# Duplicate name
$dup = Invoke-Request -method POST -url http://localhost:8080/api/members -body '{"name":"Rick"}'
Write-Host "POST duplicate name => Status $($dup.Status) Body $($dup.Body)"
# Invalid expense amount (negative)
$badExp = @{ description = "Bad"; amount = -100; payerId = $memberIds["Riya"] } | ConvertTo-Json -Compress
$badRes = Invoke-Request -method POST -url http://localhost:8080/api/expenses -body $badExp
Write-Host "POST invalid expense => Status $($badRes.Status) Body $($badRes.Body)"
# Nonexistent payer
$badExp2 = @{ description = "Bad2"; amount = 100; payerId = 9999 } | ConvertTo-Json -Compress
$badRes2 = Invoke-Request -method POST -url http://localhost:8080/api/expenses -body $badExp2
Write-Host "POST expense nonexistent payer => Status $($badRes2.Status) Body $($badRes2.Body)"
