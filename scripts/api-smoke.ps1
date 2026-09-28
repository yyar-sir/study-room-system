# ============================================================
#  Shared Study Room - API smoke / regression test
#  Usage:  powershell -File scripts/api-smoke.ps1 [-Base http://127.0.0.1:8080]
#
#  Design notes
#  ------------
#  * This file is saved as UTF-8 **WITH BOM** and must stay that way: Windows
#    PowerShell reads a BOM-less .ps1 as ANSI, and Chinese text then gets
#    mis-decoded in a way that can even swallow the following newline,
#    silently commenting out code (this project was bitten by exactly that bug).
#  * HTTP request bodies are encoded to explicit UTF-8 bytes, because
#    Invoke-WebRequest -Body <string> does NOT use UTF-8 (Chinese payloads
#    arrived mangled until this was fixed).
#  * REPEATABLE and DETERMINISTIC:
#      - The business flow runs on a freshly registered throwaway user.
#      - Conflict / adjacency / daily-quota checks run on FIXED future dates with
#        FIXED slots, so they never depend on wall-clock time and cannot collide
#        with leftovers from a previous run.
#      - Only the check-in test is bound to "now", because the check-in window is
#        defined relative to the reservation start time.
#      - Everything the test creates is cancelled at the end.
#  * Assertions on remaining times are RELATIVE (+10 on purchase, -1 on check-in).
# ============================================================
param(
    [string]$Base = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

# Guard: this file MUST stay UTF-8 **with BOM** (see header). Some editors/tools
# rewrite a file without the BOM, after which Windows PowerShell reads the Chinese
# literals below as ANSI garbage and the content-safety assertion silently stops
# testing anything. Fail loudly instead of silently passing.
try {
    $selfBytes = [System.IO.File]::ReadAllBytes($PSCommandPath)
    $hasBom = ($selfBytes.Length -ge 3 -and $selfBytes[0] -eq 239 -and $selfBytes[1] -eq 187 -and $selfBytes[2] -eq 191)
    if (-not $hasBom) {
        Write-Host "[WARN] api-smoke.ps1 is missing its UTF-8 BOM." -ForegroundColor Yellow
        Write-Host "       Chinese assertions may be corrupted - re-save the file with a BOM." -ForegroundColor Yellow
        $script:fail++
    }
} catch { }

function Call([string]$method, [string]$path, $body, [string]$token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $uri = "$Base$path"
    try {
        if ($null -ne $body) {
            # Encode the payload as explicit UTF-8 bytes.
            # Invoke-WebRequest -Body <string> does NOT encode as UTF-8, so any Chinese in
            # the payload arrives mangled (the AI tests silently returned "I don't
            # understand" until this was fixed). Always send bytes for JSON with CJK text.
            $json = $body | ConvertTo-Json -Depth 6 -Compress
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            $resp = Invoke-WebRequest -Uri $uri -Method $method -Headers $headers `
                -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing -TimeoutSec 60
        } else {
            $resp = Invoke-WebRequest -Uri $uri -Method $method -Headers $headers `
                -UseBasicParsing -TimeoutSec 20
        }
        $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = ($text | ConvertFrom-Json) }
    } catch {
        $sc = 0; $text = ''
        if ($_.Exception.Response) {
            $sc = [int]$_.Exception.Response.StatusCode
            try {
                $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
                $text = $sr.ReadToEnd()
            } catch { }
        }
        $parsed = $null
        if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { } }
        return [pscustomobject]@{ Status = $sc; Body = $parsed }
    }
}

function Check([string]$name, [bool]$cond, [string]$detail) {
    if ($cond) { $script:pass++; Write-Host ("  [PASS] " + $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  [FAIL] " + $name + "  -> " + $detail) -ForegroundColor Red }
}

# True when the object actually carries the named property.
# Do NOT use ($null -eq $obj.prop): member access on an array returns an empty
# array, and ($null -eq @()) is False in PowerShell - a check that can never pass.
function HasProp($obj, [string]$name) {
    if ($null -eq $obj) { return $false }
    return @($obj.PSObject.Properties.Name) -contains $name
}

function UsableRemain($membershipsResponse) {
    if ($null -eq $membershipsResponse) { return $null }
    # Must unwrap .Body first - the Call wrapper exposes Status/Body.
    $body = $membershipsResponse.Body
    if ($null -eq $body -or $null -eq $body.data) { return $null }
    $u = @($body.data | Where-Object { $_.usable -eq $true })
    if ($u.Count -eq 0) { return $null }
    return $u[0].remainTimes
}

function FreeSeats([string]$date, [string]$from, [string]$to, [string]$token) {
    $r = Call 'GET' "/api/seats?storeId=1&date=$date&startTime=$from&endTime=$to" $null $token
    return @(@($r.Body.data) | Where-Object { $_.bookable -eq $true })
}

Write-Host "`n=== 1. Health / public endpoints ===" -ForegroundColor Cyan
$ping = Call 'GET' '/api/public/ping' $null $null
Check 'public ping reachable' ($ping.Status -eq 200 -and $ping.Body.code -eq 0) "status=$($ping.Status)"

$plans = Call 'GET' '/api/public/plans' $null $null
Check 'public plan list (>=4)' ($plans.Status -eq 200 -and @($plans.Body.data).Count -ge 4) "count=$(@($plans.Body.data).Count)"
Check 'plan payload has no password field' (-not (HasProp @($plans.Body.data)[0] 'password')) 'password present'

# 门店列表必须公开：用户端选座前要先能挑门店，只有经营者接口是不够的
$pubStores = Call 'GET' '/api/public/stores' $null $null
Check 'public store list is reachable anonymously' ($pubStores.Status -eq 200 -and @($pubStores.Body.data).Count -ge 1) "count=$(@($pubStores.Body.data).Count)"

# 预约规则也必须公开：前端选座页要展示"最多提前几天、单次最长几小时"，
# 若前端自己硬编码一份，改了后台配置就会前后端不一致
$pubRules = Call 'GET' '/api/public/rules' $null $null
Check 'public booking rules reachable anonymously' ($pubRules.Status -eq 200 -and $pubRules.Body.code -eq 0) "code=$($pubRules.Body.code)"
Check 'rules expose maxReserveHours' (HasProp $pubRules.Body.data 'maxReserveHours') 'missing maxReserveHours'
Check 'rules expose advanceBookDays' (HasProp $pubRules.Body.data 'advanceBookDays') 'missing advanceBookDays'

Write-Host "`n=== 2. Login (three demo roles) ===" -ForegroundColor Cyan
$tok = @{}
foreach ($a in @(
        @{ p = '13900000000'; n = 'owner'; r = 0 },
        @{ p = '13900000001'; n = 'staff'; r = 1 },
        @{ p = '13900000002'; n = 'user';  r = 2 })) {
    $r = Call 'POST' '/api/auth/login' @{ phone = $a.p; password = '123456' } $null
    $ok = ($r.Status -eq 200 -and $r.Body.code -eq 0 -and $r.Body.data.role -eq $a.r)
    Check "login $($a.n) role=$($a.r)" $ok "status=$($r.Status) code=$($r.Body.code)"
    if ($ok) {
        $tok[$a.n] = $r.Body.data.token
        Check "login $($a.n) response hides password" (-not (HasProp $r.Body.data 'password')) 'password field present'
    }
}
$badPwd = Call 'POST' '/api/auth/login' @{ phone = '13900000002'; password = 'definitely-wrong' } $null
Check 'wrong password -> 1003' ($badPwd.Body.code -eq 1003) "code=$($badPwd.Body.code)"

Write-Host "`n=== 3. Auth / role isolation ===" -ForegroundColor Cyan
Check 'anonymous -> 401' ((Call 'GET' '/api/user/memberships' $null $null).Status -eq 401) 'expected 401'
Check 'staff calling user API -> 403' ((Call 'GET' '/api/user/memberships' $null $tok['staff']).Status -eq 403) 'expected 403'
Check 'user calling owner API -> 403' ((Call 'GET' '/api/owner/stores' $null $tok['user']).Status -eq 403) 'expected 403'
Check 'staff calling owner API -> 403' ((Call 'GET' '/api/owner/stores' $null $tok['staff']).Status -eq 403) 'expected 403'
Check 'owner calling owner API -> 200' ((Call 'GET' '/api/owner/stores' $null $tok['owner']).Status -eq 200) 'expected 200'
Check 'user calling staff checkin API -> 403' ((Call 'POST' '/api/staff/checkin' @{ checkinCode = 'AAAAAAAA' } $tok['user']).Status -eq 403) 'expected 403'

Write-Host "`n=== 4. Fresh user registration + membership purchase ===" -ForegroundColor Cyan
$newPhone = '137' + (Get-Random -Minimum 10000000 -Maximum 99999999)
$reg = Call 'POST' '/api/auth/register' @{ phone = $newPhone; password = '123456'; nickname = 'smoke-user' } $null
Check "register throwaway user ($newPhone)" ($reg.Status -eq 200 -and $reg.Body.code -eq 0) "code=$($reg.Body.code) msg=$($reg.Body.msg)"
$ut = $reg.Body.data.token
Check 'register returns token' (-not [string]::IsNullOrEmpty($ut)) 'no token'
Check 'register response hides password' (-not (HasProp $reg.Body.data 'password')) 'password field present'

$dupReg = Call 'POST' '/api/auth/register' @{ phone = $newPhone; password = '123456'; nickname = 'dup' } $null
Check 'duplicate phone rejected (1005)' ($dupReg.Body.code -eq 1005) "code=$($dupReg.Body.code)"

$remainBefore = UsableRemain (Call 'GET' '/api/user/memberships' $null $ut)
if ($null -eq $remainBefore) { $remainBefore = 0 }
$buy = Call 'POST' '/api/user/memberships/purchase' @{ planId = 1; payType = 1 } $ut
Check 'purchase plan #1' ($buy.Status -eq 200 -and $buy.Body.code -eq 0) "code=$($buy.Body.code) msg=$($buy.Body.msg)"
$remainAfterBuy = UsableRemain (Call 'GET' '/api/user/memberships' $null $ut)
Check 'times granted (+10 relative)' ($remainAfterBuy -eq ($remainBefore + 10)) "before=$remainBefore after=$remainAfterBuy"

Write-Host "`n=== 5. Seat map ===" -ForegroundColor Cyan
$today = (Get-Date).Date.ToString('yyyy-MM-dd')
$seats = Call 'GET' "/api/seats?storeId=1&date=$today&startTime=09:00&endTime=11:00" $null $ut
$seatList = @($seats.Body.data)
Check 'seat map returned 30 seats' ($seatList.Count -eq 30) "count=$($seatList.Count)"
Check 'maintenance seats reported (2)' (@($seatList | Where-Object { $_.status -eq 1 }).Count -eq 2) 'maintenance count mismatch'
Check 'seat payload exposes row/col for grid layout' (($null -ne $seatList[0].rowIndex) -and ($null -ne $seatList[0].colIndex)) 'missing grid coords'

Write-Host "`n=== 6. Reservation + conflict detection (fixed future date) ===" -ForegroundColor Cyan
# Fixed date/slot => independent of wall clock and of leftovers from prior runs.
$dC = (Get-Date).Date.AddDays(3).ToString('yyyy-MM-dd')
$created = @()
$free = FreeSeats $dC '09:00' '11:00' $ut
Check 'free seats available on test date' ($free.Count -ge 2) "free=$($free.Count)"
$seatA = $free[0].id
$seatB = $free[1].id

$r1 = Call 'POST' '/api/user/reservations' @{ seatId = $seatA; reserveDate = $dC; startTime = '09:00'; endTime = '11:00' } $ut
Check 'create reservation' ($r1.Status -eq 200 -and $r1.Body.code -eq 0) "code=$($r1.Body.code) msg=$($r1.Body.msg)"
if ($r1.Body.code -eq 0) { $created += $r1.Body.data.id }

$dup = Call 'POST' '/api/user/reservations' @{ seatId = $seatA; reserveDate = $dC; startTime = '09:00'; endTime = '11:00' } $ut
Check 'same seat+slot rejected (2002)' ($dup.Body.code -eq 2002) "code=$($dup.Body.code)"

$selfOverlap = Call 'POST' '/api/user/reservations' @{ seatId = $seatB; reserveDate = $dC; startTime = '10:00'; endTime = '12:00' } $ut
Check 'same user PARTIAL overlap rejected (2002)' ($selfOverlap.Body.code -eq 2002) "code=$($selfOverlap.Body.code)"

# Adjacent slot on the SAME seat must be allowed: touching boundaries are legitimate.
$adj = Call 'POST' '/api/user/reservations' @{ seatId = $seatA; reserveDate = $dC; startTime = '11:00'; endTime = '13:00' } $ut
Check 'adjacent slot allowed (no false conflict)' ($adj.Body.code -eq 0) "code=$($adj.Body.code) msg=$($adj.Body.msg)"
if ($adj.Body.code -eq 0) { $created += $adj.Body.data.id }

# Room in the grid must be reflected after booking.
$after = FreeSeats $dC '09:00' '11:00' $ut
Check 'booked seat now marked unavailable' (-not (@($after | ForEach-Object { $_.id }) -contains $seatA)) 'seatA still bookable'

# 单次预约时长上限（本次修复的核心回归断言）。
# 营业时间是 08:00-22:00，若不限制时长，用户可以一次预约 14 小时把座位整天锁死，
# 其他用户全天都约不到 —— 这是运营上必须挡住的。
#
# ⚠️ 必须用一个全新日期：$dC 上已经两次预约用掉了当日配额（默认 2 次），
#    否则边界用例会先被「每日上限」拦下（4003），就测不到时长规则本身了。
$dB = (Get-Date).Date.AddDays(4).ToString('yyyy-MM-dd')
$freeB = FreeSeats $dB '15:00' '16:00' $ut
if ($freeB.Count -ge 1) {
    $bseat = $freeB[0].id

    # 整段营业时间 08:00-22:00（14 小时）必须被拒
    $tooLong = Call 'POST' '/api/user/reservations' @{ seatId = $bseat; reserveDate = $dB; startTime = '08:00'; endTime = '22:00' } $ut
    Check 'over-long reservation rejected (1002)' ($tooLong.Body.code -eq 1002) "code=$($tooLong.Body.code) msg=$($tooLong.Body.msg)"

    # 边界：正好等于上限（默认 4 小时）必须放行，否则规则就过严了
    $exact = Call 'POST' '/api/user/reservations' @{ seatId = $bseat; reserveDate = $dB; startTime = '15:00'; endTime = '19:00' } $ut
    Check 'reservation exactly at the limit is allowed' ($exact.Body.code -eq 0) "code=$($exact.Body.code) msg=$($exact.Body.msg)"
    if ($exact.Body.code -eq 0) { $created += $exact.Body.data.id }

    # 比上限多 30 分钟就该被拒，验证比较用的是「严格大于」
    $over = Call 'POST' '/api/user/reservations' @{ seatId = $bseat; reserveDate = $dB; startTime = '14:00'; endTime = '18:30' } $ut
    Check 'over-limit by 30min rejected (1002)' ($over.Body.code -eq 1002) "code=$($over.Body.code)"
}

Write-Host "`n=== 6b. Daily quota (separate fixed date) ===" -ForegroundColor Cyan
$dQ = (Get-Date).Date.AddDays(2).ToString('yyyy-MM-dd')
$qf = FreeSeats $dQ '09:00' '11:00' $ut
if ($qf.Count -ge 3) {
    $b1 = Call 'POST' '/api/user/reservations' @{ seatId = $qf[0].id; reserveDate = $dQ; startTime = '09:00'; endTime = '11:00' } $ut
    Check 'daily quota: 1st booking ok' ($b1.Body.code -eq 0) "code=$($b1.Body.code)"
    $b2 = Call 'POST' '/api/user/reservations' @{ seatId = $qf[1].id; reserveDate = $dQ; startTime = '11:00'; endTime = '13:00' } $ut
    Check 'daily quota: 2nd booking ok' ($b2.Body.code -eq 0) "code=$($b2.Body.code)"
    $b3 = Call 'POST' '/api/user/reservations' @{ seatId = $qf[2].id; reserveDate = $dQ; startTime = '13:00'; endTime = '15:00' } $ut
    Check 'daily limit enforced (4003)' ($b3.Body.code -eq 4003) "code=$($b3.Body.code) msg=$($b3.Body.msg)"
    if ($b1.Body.code -eq 0) { $created += $b1.Body.data.id }
    if ($b2.Body.code -eq 0) { $created += $b2.Body.data.id }
} else {
    Check 'daily quota scenario' $false "only $($qf.Count) free seats on $dQ"
}

Write-Host "`n=== 7. Check-in + idempotent deduction (bound to now) ===" -ForegroundColor Cyan
$now = Get-Date
$sdt = $now.AddMinutes(2)
$todayStart = $sdt.ToString('HH:mm')
$todayEnd = $sdt.AddHours(2).ToString('HH:mm')
$tf = FreeSeats $today $todayStart $todayEnd $ut
if ($tf.Count -ge 1) {
    $cr = Call 'POST' '/api/user/reservations' @{ seatId = $tf[0].id; reserveDate = $today; startTime = $todayStart; endTime = $todayEnd } $ut
    Check 'create today reservation for check-in' ($cr.Body.code -eq 0) "code=$($cr.Body.code) msg=$($cr.Body.msg)"
    if ($cr.Body.code -eq 0) {
        $resId = $cr.Body.data.id
        $code = $cr.Body.data.checkinCode

        $ci = Call 'POST' '/api/user/checkin' @{ checkinCode = $code } $ut
        Check 'check-in succeeds' ($ci.Status -eq 200 -and $ci.Body.code -eq 0) "code=$($ci.Body.code) msg=$($ci.Body.msg)"

        $again = Call 'POST' '/api/user/checkin' @{ checkinCode = $code } $ut
        Check 'duplicate check-in rejected (2005)' ($again.Body.code -eq 2005) "code=$($again.Body.code)"

        $remainAfterCheckin = UsableRemain (Call 'GET' '/api/user/memberships' $null $ut)
        Check 'times deducted exactly once (-1 relative)' ($remainAfterCheckin -eq ($remainAfterBuy - 1)) "before=$remainAfterBuy after=$remainAfterCheckin"

        $rec = Call 'GET' '/api/user/consumption-records' $null $ut
        Check 'ledger has exactly ONE check-in deduction' (@(@($rec.Body.data) | Where-Object { $_.bizType -eq 2 }).Count -eq 1) 'count mismatch'
        Check 'ledger recorded the purchase' (@(@($rec.Body.data) | Where-Object { $_.bizType -eq 1 }).Count -eq 1) 'count mismatch'

        $badCode = Call 'POST' '/api/user/checkin' @{ checkinCode = 'ZZZZZZZZ' } $ut
        Check 'invalid check-in code rejected (2004)' ($badCode.Body.code -eq 2004) "code=$($badCode.Body.code)"

        $out = Call 'POST' "/api/user/checkout/$resId" $null $ut
        Check 'check-out succeeds' ($out.Status -eq 200 -and $out.Body.code -eq 0) "code=$($out.Body.code) msg=$($out.Body.msg)"

        $outAgain = Call 'POST' "/api/user/checkout/$resId" $null $ut
        Check 'double check-out rejected (2003)' ($outAgain.Body.code -eq 2003) "code=$($outAgain.Body.code)"

        $me = Call 'GET' '/api/user/me' $null $ut
        Check 'user/me hides password' (-not (HasProp $me.Body.data 'password')) 'password leaked'
        Check 'study minutes accumulated field present' (HasProp $me.Body.data 'totalStudyMinutes') 'missing field'
    }
} else {
    Check 'check-in flow' $false 'no free seat in the current window'
}

Write-Host "`n=== 8. Cancel rules ===" -ForegroundColor Cyan
$dT = (Get-Date).Date.AddDays(1).ToString('yyyy-MM-dd')
$cf = FreeSeats $dT '09:00' '11:00' $ut
if ($cf.Count -ge 1) {
    $r2 = Call 'POST' '/api/user/reservations' @{ seatId = $cf[0].id; reserveDate = $dT; startTime = '09:00'; endTime = '11:00' } $ut
    Check 'create future reservation' ($r2.Body.code -eq 0) "code=$($r2.Body.code) msg=$($r2.Body.msg)"
    if ($r2.Body.code -eq 0) {
        $cid = $r2.Body.data.id
        $cancel = Call 'POST' "/api/user/reservations/$cid/cancel" @{ reason = 'smoke test' } $ut
        Check 'cancel future reservation' ($cancel.Status -eq 200 -and $cancel.Body.code -eq 0) "code=$($cancel.Body.code) msg=$($cancel.Body.msg)"
        $again2 = Call 'POST' "/api/user/reservations/$cid/cancel" @{ } $ut
        Check 'double cancel rejected (2003)' ($again2.Body.code -eq 2003) "code=$($again2.Body.code)"
        # Cancelled slot must be re-bookable (regression guard for uk_res_active_slot).
        $re = Call 'POST' '/api/user/reservations' @{ seatId = $cf[0].id; reserveDate = $dT; startTime = '09:00'; endTime = '11:00' } $ut
        Check 'cancelled slot can be re-booked' ($re.Body.code -eq 0) "code=$($re.Body.code) msg=$($re.Body.msg)"
        if ($re.Body.code -eq 0) { $created += $re.Body.data.id }
    }
} else {
    Check 'cancel flow' $false 'no free seat tomorrow'
}

Write-Host "`n=== 9. AI assistant (knowledge base + safety + degradation) ===" -ForegroundColor Cyan
# Uses the throwaway user so each run starts with a fresh per-user AI rate-limit window.
$cfgMasked = Call 'GET' '/api/owner/ai/config' $null $tok['owner']
Check 'owner can read AI config' ($cfgMasked.Status -eq 200 -and $cfgMasked.Body.code -eq 0) "code=$($cfgMasked.Body.code)"
$maskedKey = $cfgMasked.Body.data.apiKey
Check 'AI apiKey is masked (not the real key)' `
    (($maskedKey -eq '') -or ($maskedKey -like '*****')) "apiKey=$maskedKey"
Check 'AI config exposes rate limit' ($null -ne $cfgMasked.Body.data.rateLimitPerMin) 'missing rateLimitPerMin'
Check 'user cannot read AI config -> 403' ((Call 'GET' '/api/owner/ai/config' $null $ut).Status -eq 403) 'expected 403'

# Content safety is decided before any model call, so this assertion is deterministic
# and free (no tokens spent).
$unsafe = Call 'POST' '/api/user/ai/ask' @{ question = '教我作弊通过考试' } $ut
Check 'AI content safety blocks forbidden input' ($unsafe.Body.code -eq 0 -and $unsafe.Body.data.intent -eq 'blocked') "intent=$($unsafe.Body.data.intent)"

# One real ask. Tolerant by design: must answer successfully whether the model is
# reachable (degraded=false) or not (degraded=true, knowledge-base fallback).
$ask = Call 'POST' '/api/user/ai/ask' @{ question = '套餐有哪些'; sessionId = 'smoke' } $ut
Check 'AI ask returns code 0 (never 500)' ($ask.Status -eq 200 -and $ask.Body.code -eq 0) "status=$($ask.Status) code=$($ask.Body.code)"
Check 'AI ask returns a non-empty answer' (-not [string]::IsNullOrWhiteSpace($ask.Body.data.answer)) 'empty answer'
Check 'AI answer carries degraded flag for UI hinting' ($null -ne $ask.Body.data.degraded) 'missing degraded flag'
Check 'AI intent detected for plan question' ($ask.Body.data.intent -eq 'plan_query') "intent=$($ask.Body.data.intent)"

$hist = Call 'GET' '/api/user/ai/history?sessionId=smoke' $null $ut
Check 'AI history recorded for session' (@($hist.Body.data).Count -ge 2) "count=$(@($hist.Body.data).Count)"

Write-Host "`n=== 10. Statistics / staff ops / notifications ===" -ForegroundColor Cyan
$ov = Call 'GET' '/api/owner/stats/overview' $null $tok['owner']
Check 'owner reads stats overview' ($ov.Status -eq 200 -and $ov.Body.code -eq 0) "code=$($ov.Body.code)"
Check 'overview exposes seatTotal + occupancyRate' `
    ((HasProp $ov.Body.data 'seatTotal') -and (HasProp $ov.Body.data 'occupancyRate')) 'missing KPI fields'
Check 'staff cannot read business stats -> 403' ((Call 'GET' '/api/owner/stats/overview' $null $tok['staff']).Status -eq 403) 'expected 403'
Check 'user cannot read business stats -> 403' ((Call 'GET' '/api/owner/stats/overview' $null $ut).Status -eq 403) 'expected 403'

$hr = Call 'GET' '/api/owner/stats/hourly-traffic' $null $tok['owner']
Check 'hourly traffic covers 14 business hours' (@($hr.Body.data).Count -eq 14) "count=$(@($hr.Body.data).Count)"
$rt = Call 'GET' '/api/owner/stats/revenue-trend?days=7' $null $tok['owner']
Check 'revenue trend returns 7 points' (@($rt.Body.data).Count -eq 7) "count=$(@($rt.Body.data).Count)"
$rt2 = Call 'GET' '/api/owner/stats/reservation-trend?days=999' $null $tok['owner']
Check 'trend days is clamped (<=90)' (@($rt2.Body.data).Count -le 90) "count=$(@($rt2.Body.data).Count)"

$ss = Call 'GET' '/api/staff/seats' $null $tok['staff']
Check 'staff lists own-store seats' (@($ss.Body.data).Count -eq 30) "count=$(@($ss.Body.data).Count)"
Check 'user cannot list staff seats -> 403' ((Call 'GET' '/api/staff/seats' $null $ut).Status -eq 403) 'expected 403'

# Toggle a seat to maintenance and back, so the gate leaves no residue.
$seatId = @($ss.Body.data)[0].id
$mark = Call 'POST' "/api/staff/seats/$seatId/status" @{ status = 1; remark = 'smoke: maintenance' } $tok['staff']
Check 'staff sets seat to maintenance' ($mark.Body.code -eq 0) "code=$($mark.Body.code) msg=$($mark.Body.msg)"
$bad = Call 'POST' "/api/staff/seats/$seatId/status" @{ status = 9 } $tok['staff']
Check 'invalid seat status rejected (1002)' ($bad.Body.code -eq 1002) "code=$($bad.Body.code)"
$restore = Call 'POST' "/api/staff/seats/$seatId/status" @{ status = 0 } $tok['staff']
Check 'staff restores seat to available' ($restore.Body.code -eq 0) "code=$($restore.Body.code)"

$vio = Call 'GET' '/api/staff/violations' $null $tok['staff']
Check 'staff can list violations' ($vio.Status -eq 200 -and $vio.Body.code -eq 0) "code=$($vio.Body.code)"
Check 'user cannot list violations -> 403' ((Call 'GET' '/api/staff/violations' $null $ut).Status -eq 403) 'expected 403'

# 前台协助工作流：先看今日到店情况，再按手机号定位在座预约做签退
$todayRes = Call 'GET' '/api/staff/reservations/today' $null $tok['staff']
Check 'staff reads today reservations' ($todayRes.Status -eq 200 -and $todayRes.Body.code -eq 0) "code=$($todayRes.Body.code)"
Check 'user cannot read staff today list -> 403' ((Call 'GET' '/api/staff/reservations/today' $null $ut).Status -eq 403) 'expected 403'

$act = Call 'GET' '/api/staff/reservations/active?phone=13900000002' $null $tok['staff']
Check 'staff queries active reservation by phone' ($act.Status -eq 200 -and $act.Body.code -eq 0) "code=$($act.Body.code)"
Check 'user cannot query staff active list -> 403' ((Call 'GET' '/api/staff/reservations/active?phone=13900000002' $null $ut).Status -eq 403) 'expected 403'
$noSuch = Call 'GET' '/api/staff/reservations/active?phone=13000000000' $null $tok['staff']
Check 'unknown phone returns business error not 500' ($noSuch.Status -eq 200 -and $noSuch.Body.code -eq 1002) "code=$($noSuch.Body.code)"

$unread = Call 'GET' '/api/notifications/unread-count' $null $ut
Check 'unread count readable by user' ($unread.Status -eq 200 -and $unread.Body.code -eq 0) "code=$($unread.Body.code)"
$markAll = Call 'POST' '/api/notifications/read-all' @{} $ut
Check 'mark all notifications read' ($markAll.Body.code -eq 0) "code=$($markAll.Body.code)"
$unread2 = Call 'GET' '/api/notifications/unread-count' $null $ut
Check 'unread count resets to 0' ($unread2.Body.data -eq 0) "unread=$($unread2.Body.data)"

$ops = Call 'POST' '/api/owner/ops/run-noshow-check' @{} $tok['owner']
Check 'owner can trigger no-show check' ($ops.Body.code -eq 0) "code=$($ops.Body.code)"
Check 'user cannot trigger ops -> 403' ((Call 'POST' '/api/owner/ops/run-noshow-check' @{} $ut).Status -eq 403) 'expected 403'

Write-Host "`n=== 11. System / operating-rule configuration ===" -ForegroundColor Cyan
$cfgs = Call 'GET' '/api/owner/configs?group=rule' $null $tok['owner']
Check 'owner reads operating rule configs' ($cfgs.Status -eq 200 -and @($cfgs.Body.data).Count -ge 1) "count=$(@($cfgs.Body.data).Count)"
Check 'staff cannot read rule configs -> 403' ((Call 'GET' '/api/owner/configs' $null $tok['staff']).Status -eq 403) 'expected 403'
Check 'user cannot read rule configs -> 403' ((Call 'GET' '/api/owner/configs' $null $ut).Status -eq 403) 'expected 403'

# Upsert must overwrite in place, never append a duplicate row.
# This is the regression guard for the system_config unique-index fix
# (store_id used to be NULL for global rows, and MySQL treats NULLs as distinct,
#  so uk_config did not actually prevent duplicate global configs).
$key = 'advance_book_days'
$row = @(@($cfgs.Body.data) | Where-Object { $_.configKey -eq $key })
Check 'global config row exists' ($row.Count -eq 1) "count=$($row.Count)"
if ($row.Count -eq 1) {
    $orig = $row[0].configValue
    $null = Call 'POST' '/api/owner/configs' @{ storeId = 0; configKey = $key; configValue = $orig; configGroup = 'rule' } $tok['owner']
    $null = Call 'POST' '/api/owner/configs' @{ storeId = 0; configKey = $key; configValue = $orig; configGroup = 'rule' } $tok['owner']
    $after = Call 'GET' '/api/owner/configs?group=rule' $null $tok['owner']
    $sameKey = @(@($after.Body.data) | Where-Object { $_.configKey -eq $key })
    Check 'config upsert overwrites (no duplicate row)' ($sameKey.Count -eq 1) "count=$($sameKey.Count)"

    $changed = Call 'POST' '/api/owner/configs' @{ storeId = 0; configKey = $key; configValue = '5'; configGroup = 'rule' } $tok['owner']
    Check 'config value can be changed' ($changed.Body.code -eq 0) "code=$($changed.Body.code)"
    $check = Call 'GET' '/api/owner/configs?group=rule' $null $tok['owner']
    $val = @(@($check.Body.data) | Where-Object { $_.configKey -eq $key })[0].configValue
    Check 'changed value is persisted' ($val -eq '5') "value=$val"
    # Restore so the gate leaves the environment as it found it
    $null = Call 'POST' '/api/owner/configs' @{ storeId = 0; configKey = $key; configValue = $orig; configGroup = 'rule' } $tok['owner']
}

Write-Host "`n=== 12. Owner resource admin + staff member business ===" -ForegroundColor Cyan
$allPlans = Call 'GET' '/api/owner/plans' $null $tok['owner']
Check 'owner reads all plans (incl. delisted)' ($allPlans.Status -eq 200 -and @($allPlans.Body.data).Count -ge 4) "count=$(@($allPlans.Body.data).Count)"
Check 'staff cannot read all plans -> 403' ((Call 'GET' '/api/owner/plans' $null $tok['staff']).Status -eq 403) 'expected 403'

$stypes = Call 'GET' '/api/owner/seat-types' $null $tok['owner']
Check 'owner reads seat types' (@($stypes.Body.data).Count -ge 4) "count=$(@($stypes.Body.data).Count)"

$oseats = Call 'GET' '/api/owner/seats?storeId=1' $null $tok['owner']
Check 'owner reads seat layout' (@($oseats.Body.data).Count -eq 30) "count=$(@($oseats.Body.data).Count)"
Check 'staff cannot read owner seat layout -> 403' ((Call 'GET' '/api/owner/seats?storeId=1' $null $tok['staff']).Status -eq 403) 'expected 403'

# Seat numbers must be unique within a store (uk_seat_store_no)
$dupSeat = Call 'POST' '/api/owner/seats' @{ storeId = 1; seatNo = 'A-01'; typeId = 1 } $tok['owner']
Check 'duplicate seat number rejected (1002)' ($dupSeat.Body.code -eq 1002) "code=$($dupSeat.Body.code)"

# 前台按手机号核对会员，并代客开卡
$mb = Call 'GET' "/api/staff/members/by-phone?phone=$newPhone" $null $tok['staff']
Check 'staff looks up member by phone' ($mb.Status -eq 200 -and $mb.Body.code -eq 0) "code=$($mb.Body.code)"
Check 'member lookup returns userId' (HasProp $mb.Body.data 'userId') 'missing userId'
Check 'user cannot call staff member API -> 403' ((Call 'GET' "/api/staff/members/by-phone?phone=$newPhone" $null $ut).Status -eq 403) 'expected 403'

$badPhone = Call 'GET' '/api/staff/members/by-phone?phone=13000000000' $null $tok['staff']
Check 'unknown member phone returns 1002 not 500' ($badPhone.Body.code -eq 1002) "code=$($badPhone.Body.code)"

$grant = Call 'POST' '/api/staff/members/grant' @{ phone = $newPhone; planId = 2; payType = 1 } $tok['staff']
Check 'staff grants membership on behalf of user' ($grant.Body.code -eq 0) "code=$($grant.Body.code) msg=$($grant.Body.msg)"
Check 'granted membership carries plan name' (-not [string]::IsNullOrWhiteSpace($grant.Body.data.planName)) 'no planName'

# 关键：前台代开必须与用户自助购买口径一致，要落进账本，
# 否则前台开的卡不会出现在用户消费明细里，对账时对不上。
$recAfterGrant = Call 'GET' '/api/user/consumption-records' $null $ut
$grantRecs = @(@($recAfterGrant.Body.data) | Where-Object { $_.bizType -eq 1 })
Check 'grant is recorded in the ledger (bizType=1)' ($grantRecs.Count -ge 2) "count=$($grantRecs.Count)"

# 前台不能替用户开经营者的地盘
Check 'user cannot grant membership -> 403' ((Call 'POST' '/api/staff/members/grant' @{ phone = $newPhone; planId = 2 } $ut).Status -eq 403) 'expected 403'

Write-Host "`n=== 13. Announcements / records / staff admin / backup ===" -ForegroundColor Cyan
$annTitle = 'smoke-announce-' + (Get-Random -Minimum 1000 -Maximum 9999)
$pub = Call 'POST' '/api/staff/announcements' @{ title = $annTitle; content = 'smoke test announcement'; scope = 1 } $tok['staff']
Check 'staff can publish announcement' ($pub.Body.code -eq 0) "code=$($pub.Body.code) msg=$($pub.Body.msg)"
# 前台不得发全平台公告：服务端必须把 scope 强制改写为本门店
Check 'staff announcement is forced to own-store scope' ($pub.Body.data.scope -eq 2) "scope=$($pub.Body.data.scope)"
$annId = $pub.Body.data.id

$pubList = Call 'GET' '/api/public/announcements?storeId=1' $null $null
Check 'published announcement visible anonymously' (@(@($pubList.Body.data) | Where-Object { $_.id -eq $annId }).Count -eq 1) 'not in public list'

$off = Call 'POST' "/api/staff/announcements/$annId/offline" @{} $tok['staff']
Check 'staff offlines own announcement' ($off.Body.code -eq 0) "code=$($off.Body.code)"
$pubList2 = Call 'GET' '/api/public/announcements?storeId=1' $null $null
Check 'offlined announcement gone from public list' (@(@($pubList2.Body.data) | Where-Object { $_.id -eq $annId }).Count -eq 0) 'still visible'

$rr = Call 'GET' '/api/staff/records/reservations' $null $tok['staff']
Check 'staff reads reservation records' ($rr.Status -eq 200 -and $rr.Body.code -eq 0) "code=$($rr.Body.code)"
$rc = Call 'GET' '/api/staff/records/checkins' $null $tok['staff']
Check 'staff reads check-in records' ($rc.Status -eq 200 -and $rc.Body.code -eq 0) "code=$($rc.Body.code)"
$rp = Call 'GET' '/api/staff/records/consumptions' $null $tok['staff']
Check 'staff reads consumption records' ($rp.Status -eq 200 -and $rp.Body.code -eq 0) "code=$($rp.Body.code)"
Check 'user cannot read staff records -> 403' ((Call 'GET' '/api/staff/records/reservations' $null $ut).Status -eq 403) 'expected 403'

$cat = Call 'GET' '/api/owner/staff/permission-catalog' $null $tok['owner']
Check 'owner reads permission catalog' (@($cat.Body.data).Count -ge 5) "count=$(@($cat.Body.data).Count)"
Check 'staff cannot read permission catalog -> 403' ((Call 'GET' '/api/owner/staff/permission-catalog' $null $tok['staff']).Status -eq 403) 'expected 403'

$newStaffPhone = '136' + (Get-Random -Minimum 10000000 -Maximum 99999999)
$cs = Call 'POST' '/api/owner/staff' @{ phone = $newStaffPhone; password = '123456'; realName = 'smoke-staff'; nickname = 'smoke'; storeId = 1; permissions = @('seat:manage', 'checkin:assist') } $tok['owner']
Check 'owner creates staff account' ($cs.Body.code -eq 0) "code=$($cs.Body.code) msg=$($cs.Body.msg)"
$sl = Call 'GET' '/api/owner/staff' $null $tok['owner']
$createdStaff = @(@($sl.Body.data) | Where-Object { $_.phone -eq $newStaffPhone })
Check 'new staff appears in list' ($createdStaff.Count -eq 1) "count=$($createdStaff.Count)"
if ($createdStaff.Count -eq 1) {
    Check 'new staff keeps assigned permissions' ($createdStaff[0].permissions.Count -eq 2) "count=$($createdStaff[0].permissions.Count)"

    # 关键：通过经营者接口创建的账号必须是前台（role=1），
    # 而不是被注册接口那套默认成 role=2 的用户
    $nl = Call 'POST' '/api/auth/login' @{ phone = $newStaffPhone; password = '123456' } $null
    Check 'created staff logs in with role=1' ($nl.Body.code -eq 0 -and $nl.Body.data.role -eq 1) "code=$($nl.Body.code) role=$($nl.Body.data.role)"

    $null = Call 'POST' "/api/owner/staff/$($createdStaff[0].id)/status" @{ status = 0 } $tok['owner']
    $nl2 = Call 'POST' '/api/auth/login' @{ phone = $newStaffPhone; password = '123456' } $null
    Check 'disabled staff cannot log in (1004)' ($nl2.Body.code -eq 1004) "code=$($nl2.Body.code)"
}
Check 'user cannot create staff -> 403' ((Call 'POST' '/api/owner/staff' @{ phone = '13600000000'; password = '123456'; storeId = 1 } $ut).Status -eq 403) 'expected 403'

$bk = Call 'POST' '/api/owner/backup' @{} $tok['owner']
Check 'owner runs database backup' ($bk.Status -eq 200 -and $bk.Body.code -eq 0) "code=$($bk.Body.code)"
Check 'backup actually succeeded (status=1)' ($bk.Body.data.status -eq 1) "status=$($bk.Body.data.status) remark=$($bk.Body.data.remark)"
$bkl = Call 'GET' '/api/owner/backup' $null $tok['owner']
Check 'backup records are listed' (@($bkl.Body.data).Count -ge 1) "count=$(@($bkl.Body.data).Count)"
Check 'user cannot read backup records -> 403' ((Call 'GET' '/api/owner/backup' $null $ut).Status -eq 403) 'expected 403'

# Cleanup: release everything this run created so the gate stays repeatable.
foreach ($id in $created) {
    $null = Call 'POST' "/api/user/reservations/$id/cancel" @{ reason = 'cleanup' } $ut
}

Write-Host "`n=== 13b. Announcement push / operation log / business hours / reserve remind ===" -ForegroundColor Cyan

# ---- 公告发布必须同时产生站内通知 ----
# 早期公告只写 announcement 表，而消息中心「公告」标签页查的是 notification 表，
# 于是那个标签页永远是空的。这条守住「公告要同时落两处」。
$push = Call 'POST' '/api/staff/announcements' @{ title = 'smoke-announce-push'; content = 'gate check' } $tok['staff']
Check 'staff publishes announcement (push case)' ($push.Body.code -eq 0) "code=$($push.Body.code)"
$annNotes = Call 'GET' '/api/notifications?type=announce' $null $tok['user']
Check 'publishing announcement pushes an announce notification' (@($annNotes.Body.data).Count -gt 0) "announce notifications=$(@($annNotes.Body.data).Count)"
if ($push.Body.code -eq 0) {
    $null = Call 'POST' "/api/staff/announcements/$($push.Body.data.id)/offline" $null $tok['staff']
}

# ---- 操作日志（AOP 自动写入 + 口令脱敏）----
$logs = Call 'GET' '/api/owner/operation-logs?limit=50' $null $tok['owner']
$logRows = @($logs.Body.data)
Check 'owner can read operation logs' ($logs.Body.code -eq 0 -and $logRows.Count -gt 0) "count=$($logRows.Count)"
Check 'operation log captured a write operation' (@($logRows | Where-Object { $_.method -like 'POST *' }).Count -gt 0) 'no POST entry'
$authLogs = @($logRows | Where-Object { $_.module -eq 'Auth' })
Check 'operation log records auth writes' ($authLogs.Count -gt 0) 'no Auth entry'
# 口令绝不能以明文落库 —— 这是安全底线，独立成检查项
Check 'operation log masks passwords' (@($authLogs | Where-Object { $_.params -and $_.params -match '123456' }).Count -eq 0) 'plaintext password leaked'
Check 'operation log stores masked placeholder' (@($authLogs | Where-Object { $_.params -and $_.params -match '\*\*\*' }).Count -gt 0) 'no *** placeholder'
Check 'non-owner cannot read operation logs' ((Call 'GET' '/api/owner/operation-logs' $null $tok['user']).Status -eq 403) 'expected 403'

# ---- 营业时段 ----
$bhList = Call 'GET' '/api/owner/business-hours?storeId=1' $null $tok['owner']
Check 'owner can list business hours' ($bhList.Body.code -eq 0 -and @($bhList.Body.data).Count -gt 0) "count=$(@($bhList.Body.data).Count)"
Check 'non-owner cannot list business hours' ((Call 'GET' '/api/owner/business-hours?storeId=1' $null $tok['user']).Status -eq 403) 'expected 403'
# 22:00-23:00 与种子里最后一段（18:00-22:00）首尾相接，不重叠，应当允许
$bhNew = Call 'POST' '/api/owner/business-hours' @{ storeId = 1; name = 'gate-slot'; startTime = '22:00:00'; endTime = '23:00:00'; bookable = 0; sort = 99 } $tok['owner']
Check 'owner can create a business hour' ($bhNew.Body.code -eq 0) "code=$($bhNew.Body.code) msg=$($bhNew.Body.msg)"
$bhDup = Call 'POST' '/api/owner/business-hours' @{ storeId = 1; name = 'gate-overlap'; startTime = '22:30:00'; endTime = '23:30:00'; bookable = 0 } $tok['owner']
Check 'overlapping business hour rejected' ($bhDup.Body.code -eq 1002) "code=$($bhDup.Body.code)"
Check 'business hour rejects end before start' ((Call 'POST' '/api/owner/business-hours' @{ storeId = 1; name = 'gate-bad'; startTime = '10:00:00'; endTime = '09:00:00' } $tok['owner']).Body.code -eq 1002) 'expected 1002'
if ($bhNew.Body.code -eq 0) {
    $null = Call 'POST' "/api/owner/business-hours/$($bhNew.Body.data.id)/remove" $null $tok['owner']
}

# ---- 运维：预约提醒的手工触发入口 ----
$remind = Call 'POST' '/api/owner/ops/run-reserve-remind' $null $tok['owner']
Check 'owner can trigger reserve remind' ($remind.Body.code -eq 0) "code=$($remind.Body.code) msg=$($remind.Body.msg)"
Check 'reserve remind is owner-only' ((Call 'POST' '/api/owner/ops/run-reserve-remind' $null $tok['user']).Status -eq 403) 'expected 403'

Write-Host "`n=== 13c. Staff cancels a reservation on behalf of a user ===" -ForegroundColor Cyan
# 用 +5 天：前面已占用 today / +1 / +2 / +3 / +4，避免撞每日配额。
# 用本次新注册的临时账号 $ut 而不是演示账号：演示账号可能被人工测试留下
# 同日同时段的预约，会触发「同一用户同一时段不能坐两个座位」的冲突校验。
# 本节的预约结束时会被前台取消（状态 3），不再占用时段，因此可重复执行。
$dS = (Get-Date).Date.AddDays(5).ToString('yyyy-MM-dd')
$sf = @(FreeSeats $dS '09:00' '11:00' $ut)
$cr = Call 'POST' '/api/user/reservations' @{ seatId = $sf[0].id; reserveDate = $dS; startTime = '09:00'; endTime = '11:00' } $ut
Check 'user creates reservation for staff-cancel test' ($cr.Body.code -eq 0) "code=$($cr.Body.code) msg=$($cr.Body.msg)"
$rid = $cr.Body.data.id

# 必须填原因：不填就直接拒，避免后台出现「无理由取消」的黑洞
Check 'staff cancel requires a reason' ((Call 'POST' "/api/staff/reservations/$rid/cancel" @{ reason = '' } $tok['staff']).Body.code -eq 1002) 'expected 1002'
# 普通用户不能走前台接口
Check 'user cannot call staff cancel' ((Call 'POST' "/api/staff/reservations/$rid/cancel" @{ reason = 'x' } $tok['user']).Status -eq 403) 'expected 403'

$sc = Call 'POST' "/api/staff/reservations/$rid/cancel" @{ reason = 'smoke: user called to cancel' } $tok['staff']
Check 'staff cancels reservation on behalf of user' ($sc.Body.code -eq 0) "code=$($sc.Body.code) msg=$($sc.Body.msg)"

$after = Call 'GET' "/api/user/reservations/$rid" $null $ut
Check 'cancelled reservation reports status 3' ($after.Body.data.status -eq 3) "status=$($after.Body.data.status)"
Check 'cancel reason is visible to the user' ((HasProp $after.Body.data 'cancelReason') -and $after.Body.data.cancelReason -match '前台代取消') "reason=[$($after.Body.data.cancelReason)]"
Check 'double staff cancel rejected' ((Call 'POST' "/api/staff/reservations/$rid/cancel" @{ reason = 'again' } $tok['staff']).Body.code -eq 2003) 'expected 2003'
Check 'staff cancel unknown reservation rejected' ((Call 'POST' '/api/staff/reservations/99999999/cancel' @{ reason = 'x' } $tok['staff']).Body.code -eq 2003) 'expected 2003'

# 代取消是别人动了预约，必须通知到用户
$sysNotes = Call 'GET' '/api/notifications?type=system' $null $ut
Check 'user is notified about staff cancellation' (@($sysNotes.Body.data | Where-Object { $_.title -match '已被取消' }).Count -gt 0) 'no cancellation notification'

Write-Host "`n=== 14. Profile + password recovery (runs LAST: reset invalidates tokens) ===" -ForegroundColor Cyan
# 放在最后：重置密码会写 password_changed_at，令 $ut 的所有旧 token 立即失效，
# 之后再用 $ut 调接口会变成 401。测试顺序必须尊重这个业务语义。
$upd = Call 'PUT' '/api/user/profile' @{ nickname = 'smoke-nick'; realName = 'smoke-name'; gender = 1 } $ut
Check 'user updates own profile' ($upd.Body.code -eq 0) "code=$($upd.Body.code)"
Check 'profile change persisted' ($upd.Body.data.nickname -eq 'smoke-nick') "nickname=$($upd.Body.data.nickname)"

# 资料接口不能成为提权入口：传了 role/status 也必须被忽略
$esc = Call 'PUT' '/api/user/profile' @{ nickname = 'smoke-nick'; role = 0; status = 0 } $ut
Check 'profile API ignores role/status (no privilege escalation)' `
    ($esc.Body.data.role -eq 2 -and $esc.Body.data.status -eq 1) "role=$($esc.Body.data.role) status=$($esc.Body.data.status)"

$fp = Call 'POST' '/api/auth/forgot-password/send-code' @{ phone = $newPhone } $null
Check 'password reset code issued' ($fp.Body.code -eq 0 -and $fp.Body.data.code.Length -eq 6) "code=$($fp.Body.code)"
$codeVal = $fp.Body.data.code

$badCode = Call 'POST' '/api/auth/forgot-password/reset' @{ phone = $newPhone; code = '000000'; newPassword = 'newpass123' } $null
Check 'wrong reset code rejected' ($badCode.Body.code -eq 1002) "code=$($badCode.Body.code)"

$rs = Call 'POST' '/api/auth/forgot-password/reset' @{ phone = $newPhone; code = $codeVal; newPassword = 'newpass123' } $null
Check 'reset password succeeds' ($rs.Body.code -eq 0) "code=$($rs.Body.code) msg=$($rs.Body.msg)"

$nl3 = Call 'POST' '/api/auth/login' @{ phone = $newPhone; password = 'newpass123' } $null
Check 'login with NEW password works' ($nl3.Body.code -eq 0) "code=$($nl3.Body.code)"
$old3 = Call 'POST' '/api/auth/login' @{ phone = $newPhone; password = '123456' } $null
Check 'login with OLD password now fails (1003)' ($old3.Body.code -eq 1003) "code=$($old3.Body.code)"

# 验证码必须一次性：否则泄露过的码可以被反复利用
$reuse = Call 'POST' '/api/auth/forgot-password/reset' @{ phone = $newPhone; code = $codeVal; newPassword = 'another123' } $null
Check 'reset code cannot be reused' ($reuse.Body.code -ne 0) "code=$($reuse.Body.code)"

$unknown = Call 'POST' '/api/auth/forgot-password/send-code' @{ phone = '13000000000' } $null
Check 'reset code for unknown phone rejected' ($unknown.Body.code -eq 1002) "code=$($unknown.Body.code)"

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host ("  RESULT:  PASS=$($script:pass)   FAIL=$($script:fail)") -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "============================================`n" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
