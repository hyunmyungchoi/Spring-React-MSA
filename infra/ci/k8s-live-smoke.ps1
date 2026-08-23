param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$vars = @{}
Get-Content -LiteralPath (Join-Path $Root 'infra\vm\.env.local') | ForEach-Object {
  $line = $_.Trim()
  if ($line -and !$line.StartsWith('#') -and $line.Contains('=')) {
    $parts = $line -split '=', 2
    $vars[$parts[0].Trim()] = $parts[1].Trim().Trim('"')
  }
}
foreach ($name in 'ADMIN_BOOTSTRAP_LOGIN_ID','ADMIN_BOOTSTRAP_PASSWORD') {
  if (-not $vars[$name]) { throw "Missing $name" }
}

function New-WebSession([string]$BaseUrl) {
  $handler = [Net.Http.HttpClientHandler]::new()
  $handler.AllowAutoRedirect = $false
  $handler.UseCookies = $true
  $handler.CookieContainer = [Net.CookieContainer]::new()
  $client = [Net.Http.HttpClient]::new($handler)
  return [pscustomobject]@{ Base=[Uri]$BaseUrl; Handler=$handler; Client=$client }
}
function Send-Request($Session,[string]$Method,[string]$Path,$Body=$null,[string]$Csrf='',[string]$CsrfHeader='') {
  $req = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),[Uri]::new($Session.Base,$Path))
  if ($null -ne $Body) {
    $req.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Compress),[Text.Encoding]::UTF8,'application/json')
  }
  if ($Csrf) { $req.Headers.Add($CsrfHeader,$Csrf) }
  return $Session.Client.SendAsync($req).GetAwaiter().GetResult()
}
function Body-Json($Response) {
  $text=$Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
  if (-not $text) { return $null }
  return $text | ConvertFrom-Json
}
function Assert-Success($Response,[string]$Name) {
  $status=[int]$Response.StatusCode
  Write-Output "$Name=$status"
  if (-not $Response.IsSuccessStatusCode) {
    $body=$Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    throw "$Name failed: HTTP $status body=$body"
  }
}
function Complete-OAuth($Session,[string]$StartPath) {
  $current=[Uri]::new($Session.Base,$StartPath)
  $complete=$false
  for($step=1;$step -le 30;$step++) {
    $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,$current)
    $res=$Session.Client.SendAsync($req).GetAwaiter().GetResult()
    $status=[int]$res.StatusCode
    if($status -ge 300 -and $status -lt 400) {
      $location=$res.Headers.Location
      if(-not $location.IsAbsoluteUri){$location=[Uri]::new($current,$location)}
      $current=$location
      continue
    }
    if(-not $res.IsSuccessStatusCode){throw "OAuth failed at $current with HTTP $status"}
    if($current.Host -eq $Session.Base.Host -and
       ($current.AbsolutePath -eq '/' -or $current.AbsolutePath.StartsWith('/auth'))) {
      $complete=$true
    }
    break
  }
  if(-not $complete){throw "OAuth did not reach frontend redirect for $($Session.Base.Host)"}
}
function Get-Csrf($Session,[string]$CookieName) {
  $cookie=$Session.Handler.CookieContainer.GetCookies($Session.Base)[$CookieName]
  if(-not $cookie){throw "Missing CSRF cookie $CookieName"}
  return [Uri]::UnescapeDataString($cookie.Value)
}
function Find-Id($Data) {
  if($Data -is [ValueType]){return $Data}
  $prop=$Data.PSObject.Properties|Where-Object{$_.Name -match '(^id$|Id$)'}|Select-Object -First 1
  return $prop.Value
}

$member=New-WebSession 'http://user.localtest.me'
$admin=New-WebSession 'http://admin.localtest.me'
$communityId=$null
$stockId=$null
try {
  $login=Send-Request $member 'POST' '/login/password' @{loginId=$vars.ADMIN_BOOTSTRAP_LOGIN_ID;password=$vars.ADMIN_BOOTSTRAP_PASSWORD}
  Assert-Success $login 'MEMBER_PASSWORD_LOGIN'
  Complete-OAuth $member '/bff/oauth2/authorization/member-bff'
  $me=Send-Request $member 'GET' '/bff/auth/me'
  Assert-Success $me 'MEMBER_ME'
  $meJson=Body-Json $me
  if(-not $meJson.data.authenticated){throw 'Member session is anonymous'}
  Write-Output 'MEMBER_AUTHENTICATED=True'
  $memberCsrf=Get-Csrf $member 'MEMBER-XSRF-TOKEN'

  $stamp=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $create=Send-Request $member 'POST' '/bff/community/posts' @{title="K8s smoke $stamp";content='create'} $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $create 'COMMUNITY_CREATE'
  $communityId=Find-Id (Body-Json $create).data
  if(-not $communityId){throw 'Community ID missing'}
  $read=Send-Request $member 'GET' '/bff/community/posts'
  Assert-Success $read 'COMMUNITY_READ'
  $update=Send-Request $member 'PUT' "/bff/community/posts/$communityId" @{title="K8s smoke updated $stamp";content='update'} $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $update 'COMMUNITY_UPDATE'

  $existing=Send-Request $member 'GET' '/bff/stock/watch-items'
  Assert-Success $existing 'STOCK_LIST'
  $used=@((Body-Json $existing).data|ForEach-Object{$_.symbol})
  $symbol=@('MSFT','NVDA','TSM','AMZN','GOOGL','META')|Where-Object{$_ -notin $used}|Select-Object -First 1
  if(-not $symbol){throw 'No free stock symbol'}
  $stockCreate=Send-Request $member 'POST' '/bff/stock/watch-items' @{symbol=$symbol;memo="K8s smoke $stamp"} $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $stockCreate 'STOCK_CREATE'
  $stockId=Find-Id (Body-Json $stockCreate).data
  if(-not $stockId){throw 'Stock ID missing'}
  $stockUpdate=Send-Request $member 'PUT' "/bff/stock/watch-items/$stockId" @{symbol=$symbol;memo="K8s updated $stamp"} $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $stockUpdate 'STOCK_UPDATE'

  $market=Send-Request $member 'GET' '/bff/stock/market/workspace?symbols=005930,AAPL'
  Assert-Success $market 'TOSS_MARKET'
  $marketText=$market.Content.ReadAsStringAsync().GetAwaiter().GetResult()
  if($marketText.Contains('TOSS_TOKEN_UNAVAILABLE')){throw 'Toss token unavailable'}
  Write-Output 'TOSS_TOKEN_AVAILABLE=True'

  $adminLogin=Send-Request $admin 'POST' '/login/password' @{loginId=$vars.ADMIN_BOOTSTRAP_LOGIN_ID;password=$vars.ADMIN_BOOTSTRAP_PASSWORD}
  Assert-Success $adminLogin 'ADMIN_PASSWORD_LOGIN'
  Complete-OAuth $admin '/admin-bff/oauth2/authorization/admin-bff'
  $adminMe=Send-Request $admin 'GET' '/admin-bff/auth/me'
  Assert-Success $adminMe 'ADMIN_ME'
  $adminMeJson=Body-Json $adminMe
  if(-not $adminMeJson.data.authenticated){throw 'Admin session is anonymous'}
  Write-Output 'ADMIN_AUTHENTICATED=True'
  $adminUsers=Send-Request $admin 'GET' '/admin-bff/user/admin/users'
  Assert-Success $adminUsers 'ADMIN_USERS'
  $adminSessions=Send-Request $admin 'GET' '/admin-bff/sessions/member'
  Assert-Success $adminSessions 'ADMIN_MEMBER_SESSIONS'
  $adminEvents=Send-Request $admin 'GET' '/admin-bff/sessions/member/events'
  Assert-Success $adminEvents 'ADMIN_PRESENCE_EVENTS'
  $adminCsrf=Get-Csrf $admin 'ADMIN-XSRF-TOKEN'

  $stockDelete=Send-Request $member 'DELETE' "/bff/stock/watch-items/$stockId" $null $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $stockDelete 'STOCK_DELETE'
  $stockId=$null
  $delete=Send-Request $member 'DELETE' "/bff/community/posts/$communityId" $null $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $delete 'COMMUNITY_DELETE'
  $communityId=$null

  $logout=Send-Request $member 'POST' '/bff/auth/logout' $null $memberCsrf 'X-MEMBER-XSRF-TOKEN'
  Assert-Success $logout 'MEMBER_LOGOUT'
  $after=Send-Request $member 'GET' '/bff/auth/me'
  Assert-Success $after 'MEMBER_AFTER_LOGOUT'
  if((Body-Json $after).data.authenticated){throw 'Member logout failed'}
  Write-Output 'MEMBER_LOGOUT_CLEARED=True'

  $adminLogout=Send-Request $admin 'POST' '/admin-bff/auth/logout' $null $adminCsrf 'X-ADMIN-XSRF-TOKEN'
  Assert-Success $adminLogout 'ADMIN_LOGOUT'
  $adminAfter=Send-Request $admin 'GET' '/admin-bff/auth/me'
  Assert-Success $adminAfter 'ADMIN_AFTER_LOGOUT'
  if((Body-Json $adminAfter).data.authenticated){throw 'Admin logout failed'}
  Write-Output 'ADMIN_LOGOUT_CLEARED=True'
  Write-Output 'K8S_FUNCTIONAL_SMOKE=PASS'
}
finally {
  try {
    if($stockId){
      $csrf=Get-Csrf $member 'MEMBER-XSRF-TOKEN'
      $null=Send-Request $member 'DELETE' "/bff/stock/watch-items/$stockId" $null $csrf 'X-MEMBER-XSRF-TOKEN'
    }
    if($communityId){
      $csrf=Get-Csrf $member 'MEMBER-XSRF-TOKEN'
      $null=Send-Request $member 'DELETE' "/bff/community/posts/$communityId" $null $csrf 'X-MEMBER-XSRF-TOKEN'
    }
  } catch {}
  $member.Client.Dispose(); $member.Handler.Dispose()
  $admin.Client.Dispose(); $admin.Handler.Dispose()
}
