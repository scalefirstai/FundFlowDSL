Attribute VB_Name = "DistributionEngine"
Option Explicit

' =========================================================================
' Mutual Fund Distribution Engine
' NAV-based quarterly income distribution to unitholders
'
' Entry points:
'   RunDistributionCalculation - main per-investor distribution calc
'   GenerateSummaryReport      - fund-level rollup
'   ResetOutputs               - clear output sheets
'
' Sheet contract:
'   Funds!B7:G9          fund master
'   Funds!B15:H26        period inputs (12 rows = 3 funds x 4 quarters)
'   Investors!B7:G31     investor master (25 rows)
'   Investors!B37:F<N>   holdings (Investor, Fund, Q1..Q4)
'   Tax_Rates!B7:F13     tax class -> rate by strategy
'   Tax_Rates!B18:C20    strategy -> column name
'
' All economic logic lives in this module; the workbook holds inputs only.
' This shape is intentional for the FundFlowDSL translation effort.
' =========================================================================

Private Const SHEET_FUNDS As String = "Funds"
Private Const SHEET_INV As String = "Investors"
Private Const SHEET_TAX As String = "Tax_Rates"
Private Const SHEET_DIST As String = "Distributions"
Private Const SHEET_SUM As String = "Summary"
Private Const SHEET_AUDIT As String = "Audit_Log"

Private Const FUND_FIRST_ROW As Long = 7
Private Const FUND_LAST_ROW As Long = 9
Private Const PERIOD_FIRST_ROW As Long = 15
Private Const PERIOD_LAST_ROW As Long = 26
Private Const INV_FIRST_ROW As Long = 7
Private Const INV_LAST_ROW As Long = 31
Private Const HOLD_FIRST_ROW As Long = 37
Private Const TAX_FIRST_ROW As Long = 7
Private Const TAX_LAST_ROW As Long = 13
Private Const STRAT_FIRST_ROW As Long = 18
Private Const STRAT_LAST_ROW As Long = 20

' Period column letters in Funds! period block (cols D..H)
' B=Fund, C=Period, D=EndDate, E=NAV, F=DistIncome, G=Units, H=DistPerUnit

' Quarterly column offsets in holdings table: D=Q1, E=Q2, F=Q3, G=Q4
Private Const Q1_COL As Long = 4
Private Const Q2_COL As Long = 5
Private Const Q3_COL As Long = 6
Private Const Q4_COL As Long = 7

' =========================================================================
' PUBLIC ENTRY POINTS
' =========================================================================

Public Sub RunDistributionCalculation()
    Dim t0 As Double: t0 = Timer
    On Error GoTo Handler

    Application.ScreenUpdating = False
    Application.Calculation = xlCalculationManual

    ClearDistributionsOutput

    Dim wsDist As Worksheet: Set wsDist = ThisWorkbook.Sheets(SHEET_DIST)
    Dim wsFunds As Worksheet: Set wsFunds = ThisWorkbook.Sheets(SHEET_FUNDS)
    Dim wsInv As Worksheet: Set wsInv = ThisWorkbook.Sheets(SHEET_INV)

    ' Build lookup dictionaries from sheet data
    Dim fundInfo As Object: Set fundInfo = LoadFundInfo()         ' code -> Strategy|Name
    Dim invInfo As Object: Set invInfo = LoadInvestorInfo()       ' id -> Name|Type|TaxClass|Pref
    Dim taxRates As Object: Set taxRates = LoadTaxRates()         ' "TaxClass|Strategy" -> rate
    Dim periodInfo As Object: Set periodInfo = LoadPeriodInfo()   ' "FundCode|Period" -> EndDate|NAV|DistPerUnit
    Dim holdings As Collection: Set holdings = LoadHoldings()     ' rows of investor|fund|Q1..Q4

    Dim outRow As Long: outRow = 7
    Dim rowNum As Long: rowNum = 1
    Dim qHeaders As Variant: qHeaders = Array("Q1 2025", "Q2 2025", "Q3 2025", "Q4 2025")

    Dim h As Variant
    For Each h In holdings
        Dim invId As String: invId = h(1)
        Dim fundCode As String: fundCode = h(2)
        Dim units(0 To 3) As Double
        units(0) = SafeDouble(h(3))
        units(1) = SafeDouble(h(4))
        units(2) = SafeDouble(h(5))
        units(3) = SafeDouble(h(6))

        ' Skip if missing lookups
        If Not fundInfo.Exists(fundCode) Then GoTo NextHolding
        If Not invInfo.Exists(invId) Then GoTo NextHolding

        Dim fundParts() As String: fundParts = Split(fundInfo(fundCode), "|")
        Dim strategy As String: strategy = fundParts(0)
        Dim fundName As String: fundName = fundParts(1)

        Dim invParts() As String: invParts = Split(invInfo(invId), "|")
        Dim invName As String: invName = invParts(0)
        Dim taxClass As String: taxClass = invParts(2)
        Dim pref As String: pref = invParts(3)

        Dim rateKey As String: rateKey = taxClass & "|" & strategy
        Dim rate As Double: rate = 0
        If taxRates.Exists(rateKey) Then rate = taxRates(rateKey)

        Dim qIdx As Long
        For qIdx = 0 To 3
            Dim period As String: period = CStr(qHeaders(qIdx))
            Dim pKey As String: pKey = fundCode & "|" & period
            If Not periodInfo.Exists(pKey) Then GoTo NextQ

            Dim pParts() As String: pParts = Split(periodInfo(pKey), "|")
            Dim endDate As String: endDate = pParts(0)
            Dim nav As Double: nav = CDbl(pParts(1))
            Dim distPerUnit As Double: distPerUnit = CDbl(pParts(2))

            Dim u As Double: u = units(qIdx)
            If u <= 0 Then GoTo NextQ

            Dim gross As Double: gross = u * distPerUnit
            Dim tax As Double: tax = gross * rate
            Dim net As Double: net = gross - tax
            Dim reinvUnits As Double: reinvUnits = 0
            If pref = "Reinvest" And nav > 0 Then
                reinvUnits = net / nav
            End If

            With wsDist
                .Cells(outRow, 2).Value = rowNum
                .Cells(outRow, 3).Value = invId
                .Cells(outRow, 4).Value = invName
                .Cells(outRow, 5).Value = fundCode
                .Cells(outRow, 6).Value = fundName
                .Cells(outRow, 7).Value = strategy
                .Cells(outRow, 8).Value = period
                .Cells(outRow, 9).Value = endDate
                .Cells(outRow, 10).Value = u
                .Cells(outRow, 11).Value = distPerUnit
                .Cells(outRow, 12).Value = gross
                .Cells(outRow, 13).Value = rate
                .Cells(outRow, 14).Value = tax
                .Cells(outRow, 15).Value = net
                .Cells(outRow, 16).Value = pref
                .Cells(outRow, 17).Value = reinvUnits
                .Cells(outRow, 18).Value = nav
            End With

            FormatDistributionRow wsDist, outRow
            outRow = outRow + 1
            rowNum = rowNum + 1
NextQ:
        Next qIdx
NextHolding:
    Next h

    ' Totals row
    If outRow > 7 Then
        AddDistributionTotals wsDist, outRow
    End If

    Application.Calculation = xlCalculationAutomatic
    Application.ScreenUpdating = True

    Dim elapsed As Double: elapsed = Timer - t0
    AppendAudit "RunDistributionCalculation", "Completed", _
                "Rows written: " & (rowNum - 1) & "  |  Holdings processed: " & holdings.Count, _
                Round(elapsed, 3)

    MsgBox "Distribution calculation complete." & vbCrLf & _
           (rowNum - 1) & " distribution events written to '" & SHEET_DIST & "'.", _
           vbInformation, "Done"
    Exit Sub

Handler:
    Application.Calculation = xlCalculationAutomatic
    Application.ScreenUpdating = True
    AppendAudit "RunDistributionCalculation", "ERROR", Err.Description, Err.Number
    MsgBox "Error in RunDistributionCalculation: " & Err.Description, vbCritical
End Sub


Public Sub GenerateSummaryReport()
    Dim t0 As Double: t0 = Timer
    On Error GoTo Handler

    Application.ScreenUpdating = False
    ClearSummaryOutput

    Dim wsDist As Worksheet: Set wsDist = ThisWorkbook.Sheets(SHEET_DIST)
    Dim wsSum As Worksheet: Set wsSum = ThisWorkbook.Sheets(SHEET_SUM)

    Dim lastRow As Long: lastRow = wsDist.Cells(wsDist.Rows.Count, 2).End(xlUp).Row
    If lastRow < 7 Then
        MsgBox "Run distribution calculation first.", vbExclamation
        Exit Sub
    End If

    ' Aggregate dict: "FundCode|Period" -> sums
    Dim agg As Object: Set agg = CreateObject("Scripting.Dictionary")
    Dim fundNames As Object: Set fundNames = CreateObject("Scripting.Dictionary")

    Dim r As Long
    For r = 7 To lastRow
        ' Skip totals row
        If LCase(Trim(CStr(wsDist.Cells(r, 3).Value))) = "totals" Then GoTo NextRow

        Dim fundCode As String: fundCode = CStr(wsDist.Cells(r, 5).Value)
        Dim fundName As String: fundName = CStr(wsDist.Cells(r, 6).Value)
        Dim period As String: period = CStr(wsDist.Cells(r, 8).Value)
        Dim units As Double: units = SafeDouble(wsDist.Cells(r, 10).Value)
        Dim dpu As Double: dpu = SafeDouble(wsDist.Cells(r, 11).Value)
        Dim gross As Double: gross = SafeDouble(wsDist.Cells(r, 12).Value)
        Dim tax As Double: tax = SafeDouble(wsDist.Cells(r, 14).Value)
        Dim net As Double: net = SafeDouble(wsDist.Cells(r, 15).Value)
        Dim pref As String: pref = CStr(wsDist.Cells(r, 16).Value)

        Dim key As String: key = fundCode & "|" & period
        Dim cash As Double, reinv As Double
        If pref = "Reinvest" Then
            reinv = net
        Else
            cash = net
        End If

        If Not agg.Exists(key) Then
            agg(key) = Array(units, dpu, gross, tax, net, cash, reinv)
            fundNames(fundCode) = fundName
        Else
            Dim cur As Variant: cur = agg(key)
            cur(0) = cur(0) + units
            ' dpu is identical across rows for same fund/period, keep first value
            cur(2) = cur(2) + gross
            cur(3) = cur(3) + tax
            cur(4) = cur(4) + net
            cur(5) = cur(5) + cash
            cur(6) = cur(6) + reinv
            agg(key) = cur
        End If
NextRow:
    Next r

    ' Sort keys: fund code first, then period
    Dim keys() As String
    ReDim keys(agg.Count - 1)
    Dim i As Long: i = 0
    Dim k As Variant
    For Each k In agg.Keys
        keys(i) = CStr(k)
        i = i + 1
    Next k
    QuickSort keys, LBound(keys), UBound(keys)

    Dim outRow As Long: outRow = 7
    For i = 0 To UBound(keys)
        Dim parts() As String: parts = Split(keys(i), "|")
        Dim fc As String: fc = parts(0)
        Dim pd As String: pd = parts(1)
        Dim vals As Variant: vals = agg(keys(i))

        With wsSum
            .Cells(outRow, 2).Value = fc
            .Cells(outRow, 3).Value = fundNames(fc)
            .Cells(outRow, 4).Value = pd
            .Cells(outRow, 5).Value = vals(0)   ' total units
            .Cells(outRow, 6).Value = vals(1)   ' dpu
            .Cells(outRow, 7).Value = vals(2)   ' gross
            .Cells(outRow, 8).Value = vals(3)   ' tax
            .Cells(outRow, 9).Value = vals(4)   ' net
            .Cells(outRow, 10).Value = vals(5)  ' cash
            .Cells(outRow, 11).Value = vals(6)  ' reinv
        End With
        FormatSummaryRow wsSum, outRow
        outRow = outRow + 1
    Next i

    ' Grand totals row
    If outRow > 7 Then
        AddSummaryTotals wsSum, outRow
    End If

    Application.ScreenUpdating = True

    Dim elapsed As Double: elapsed = Timer - t0
    AppendAudit "GenerateSummaryReport", "Completed", _
                "Fund-period rollups: " & agg.Count, Round(elapsed, 3)

    MsgBox "Summary report generated with " & agg.Count & " fund-period rollups.", _
           vbInformation, "Done"
    Exit Sub

Handler:
    Application.ScreenUpdating = True
    AppendAudit "GenerateSummaryReport", "ERROR", Err.Description, Err.Number
    MsgBox "Error in GenerateSummaryReport: " & Err.Description, vbCritical
End Sub


Public Sub ResetOutputs()
    On Error GoTo Handler
    Application.ScreenUpdating = False

    ClearDistributionsOutput
    ClearSummaryOutput

    Application.ScreenUpdating = True
    AppendAudit "ResetOutputs", "Completed", "Outputs cleared (audit log preserved)", 0
    MsgBox "Output sheets cleared.", vbInformation
    Exit Sub
Handler:
    Application.ScreenUpdating = True
    MsgBox "Error in ResetOutputs: " & Err.Description, vbCritical
End Sub


' =========================================================================
' LOADERS
' =========================================================================

Private Function LoadFundInfo() As Object
    Dim d As Object: Set d = CreateObject("Scripting.Dictionary")
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_FUNDS)
    Dim r As Long
    For r = FUND_FIRST_ROW To FUND_LAST_ROW
        Dim code As String: code = CStr(ws.Cells(r, 2).Value)
        If Len(code) > 0 Then
            Dim strat As String: strat = CStr(ws.Cells(r, 4).Value)
            Dim name As String: name = CStr(ws.Cells(r, 3).Value)
            d(code) = strat & "|" & name
        End If
    Next r
    Set LoadFundInfo = d
End Function

Private Function LoadInvestorInfo() As Object
    Dim d As Object: Set d = CreateObject("Scripting.Dictionary")
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_INV)
    Dim r As Long
    For r = INV_FIRST_ROW To INV_LAST_ROW
        Dim id As String: id = CStr(ws.Cells(r, 2).Value)
        If Len(id) > 0 Then
            Dim name As String: name = CStr(ws.Cells(r, 3).Value)
            Dim itype As String: itype = CStr(ws.Cells(r, 4).Value)
            Dim taxC As String: taxC = CStr(ws.Cells(r, 5).Value)
            Dim pref As String: pref = CStr(ws.Cells(r, 6).Value)
            d(id) = name & "|" & itype & "|" & taxC & "|" & pref
        End If
    Next r
    Set LoadInvestorInfo = d
End Function

Private Function LoadTaxRates() As Object
    Dim d As Object: Set d = CreateObject("Scripting.Dictionary")
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_TAX)
    Dim r As Long
    For r = TAX_FIRST_ROW To TAX_LAST_ROW
        Dim cls As String: cls = CStr(ws.Cells(r, 2).Value)
        If Len(cls) > 0 Then
            d(cls & "|Equity") = SafeDouble(ws.Cells(r, 4).Value)
            d(cls & "|Hybrid") = SafeDouble(ws.Cells(r, 5).Value)
            d(cls & "|Debt") = SafeDouble(ws.Cells(r, 6).Value)
        End If
    Next r
    Set LoadTaxRates = d
End Function

Private Function LoadPeriodInfo() As Object
    Dim d As Object: Set d = CreateObject("Scripting.Dictionary")
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_FUNDS)
    Dim r As Long
    For r = PERIOD_FIRST_ROW To PERIOD_LAST_ROW
        Dim code As String: code = CStr(ws.Cells(r, 2).Value)
        Dim period As String: period = CStr(ws.Cells(r, 3).Value)
        If Len(code) > 0 And Len(period) > 0 Then
            Dim endDate As String
            If IsDate(ws.Cells(r, 4).Value) Then
                endDate = Format(ws.Cells(r, 4).Value, "yyyy-mm-dd")
            Else
                endDate = CStr(ws.Cells(r, 4).Value)
            End If
            Dim nav As Double: nav = SafeDouble(ws.Cells(r, 5).Value)
            Dim dpu As Double: dpu = SafeDouble(ws.Cells(r, 8).Value)
            d(code & "|" & period) = endDate & "|" & nav & "|" & dpu
        End If
    Next r
    Set LoadPeriodInfo = d
End Function

Private Function LoadHoldings() As Collection
    Dim c As New Collection
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_INV)
    Dim r As Long: r = HOLD_FIRST_ROW
    Do While Len(CStr(ws.Cells(r, 2).Value)) > 0
        Dim invId As String: invId = CStr(ws.Cells(r, 2).Value)
        Dim fundCode As String: fundCode = CStr(ws.Cells(r, 3).Value)
        Dim q1 As Double: q1 = SafeDouble(ws.Cells(r, Q1_COL).Value)
        Dim q2 As Double: q2 = SafeDouble(ws.Cells(r, Q2_COL).Value)
        Dim q3 As Double: q3 = SafeDouble(ws.Cells(r, Q3_COL).Value)
        Dim q4 As Double: q4 = SafeDouble(ws.Cells(r, Q4_COL).Value)
        c.Add Array(invId, fundCode, q1, q2, q3, q4)
        r = r + 1
        If r > 500 Then Exit Do
    Loop
    Set LoadHoldings = c
End Function


' =========================================================================
' FORMATTING HELPERS
' =========================================================================

Private Sub ClearDistributionsOutput()
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_DIST)
    Dim lastRow As Long: lastRow = ws.Cells(ws.Rows.Count, 2).End(xlUp).Row
    If lastRow >= 7 Then
        ws.Range("B7:R" & lastRow).Clear
    End If
End Sub

Private Sub ClearSummaryOutput()
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_SUM)
    Dim lastRow As Long: lastRow = ws.Cells(ws.Rows.Count, 2).End(xlUp).Row
    If lastRow >= 7 Then
        ws.Range("B7:K" & lastRow).Clear
    End If
End Sub

Private Sub FormatDistributionRow(ws As Worksheet, r As Long)
    With ws.Range(ws.Cells(r, 2), ws.Cells(r, 18))
        .Font.Name = "Arial"
        .Font.Size = 10
        .Borders.LineStyle = xlContinuous
        .Borders.Color = RGB(191, 191, 191)
    End With
    ws.Cells(r, 10).NumberFormat = "#,##0.0000"
    ws.Cells(r, 11).NumberFormat = "$#,##0.0000"
    ws.Cells(r, 12).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 13).NumberFormat = "0.00%"
    ws.Cells(r, 14).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 15).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 17).NumberFormat = "#,##0.0000"
    ws.Cells(r, 18).NumberFormat = "$#,##0.0000"
End Sub

Private Sub AddDistributionTotals(ws As Worksheet, r As Long)
    ws.Cells(r, 3).Value = "TOTALS"
    Dim firstRow As Long: firstRow = 7
    Dim lastDataRow As Long: lastDataRow = r - 1
    ws.Cells(r, 12).Formula = "=SUM(L" & firstRow & ":L" & lastDataRow & ")"
    ws.Cells(r, 14).Formula = "=SUM(N" & firstRow & ":N" & lastDataRow & ")"
    ws.Cells(r, 15).Formula = "=SUM(O" & firstRow & ":O" & lastDataRow & ")"
    ws.Cells(r, 17).Formula = "=SUM(Q" & firstRow & ":Q" & lastDataRow & ")"
    With ws.Range(ws.Cells(r, 2), ws.Cells(r, 18))
        .Font.Bold = True
        .Interior.Color = RGB(217, 225, 242)
        .Borders.LineStyle = xlContinuous
    End With
    ws.Cells(r, 12).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 14).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 15).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 17).NumberFormat = "#,##0.0000"
End Sub

Private Sub FormatSummaryRow(ws As Worksheet, r As Long)
    With ws.Range(ws.Cells(r, 2), ws.Cells(r, 11))
        .Font.Name = "Arial"
        .Font.Size = 10
        .Borders.LineStyle = xlContinuous
        .Borders.Color = RGB(191, 191, 191)
    End With
    ws.Cells(r, 5).NumberFormat = "#,##0.0000"
    ws.Cells(r, 6).NumberFormat = "$#,##0.0000"
    ws.Cells(r, 7).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 8).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 9).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 10).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 11).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
End Sub

Private Sub AddSummaryTotals(ws As Worksheet, r As Long)
    ws.Cells(r, 2).Value = "TOTAL"
    Dim firstRow As Long: firstRow = 7
    Dim lastDataRow As Long: lastDataRow = r - 1
    Dim col As Long
    For col = 5 To 11
        If col <> 6 Then ' skip dpu (not meaningful to sum)
            Dim letter As String: letter = Chr(64 + col)
            ws.Cells(r, col).Formula = "=SUM(" & letter & firstRow & ":" & letter & lastDataRow & ")"
        End If
    Next col
    With ws.Range(ws.Cells(r, 2), ws.Cells(r, 11))
        .Font.Bold = True
        .Interior.Color = RGB(217, 225, 242)
        .Borders.LineStyle = xlContinuous
    End With
    ws.Cells(r, 5).NumberFormat = "#,##0.0000"
    ws.Cells(r, 7).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 8).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 9).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 10).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
    ws.Cells(r, 11).NumberFormat = "_($* #,##0.00_);_($* (#,##0.00);_($* ""-""??_);_(@_)"
End Sub


' =========================================================================
' UTILITIES
' =========================================================================

Private Function SafeDouble(v As Variant) As Double
    If IsError(v) Then
        SafeDouble = 0
    ElseIf IsNumeric(v) Then
        SafeDouble = CDbl(v)
    Else
        SafeDouble = 0
    End If
End Function

Private Sub AppendAudit(macroName As String, eventType As String, details As String, val As Variant)
    On Error Resume Next
    Dim ws As Worksheet: Set ws = ThisWorkbook.Sheets(SHEET_AUDIT)
    Dim r As Long: r = ws.Cells(ws.Rows.Count, 2).End(xlUp).Row + 1
    If r < 7 Then r = 7
    ws.Cells(r, 2).Value = Format(Now, "yyyy-mm-dd hh:mm:ss")
    ws.Cells(r, 3).Value = macroName
    ws.Cells(r, 4).Value = eventType
    ws.Cells(r, 5).Value = details
    ws.Cells(r, 6).Value = val
    With ws.Range(ws.Cells(r, 2), ws.Cells(r, 6))
        .Font.Name = "Arial"
        .Font.Size = 10
        .Borders.LineStyle = xlContinuous
        .Borders.Color = RGB(191, 191, 191)
    End With
End Sub

Private Sub QuickSort(arr() As String, lo As Long, hi As Long)
    Dim i As Long, j As Long
    Dim pivot As String, tmp As String
    i = lo: j = hi
    pivot = arr((lo + hi) \ 2)
    Do While i <= j
        Do While arr(i) < pivot: i = i + 1: Loop
        Do While arr(j) > pivot: j = j - 1: Loop
        If i <= j Then
            tmp = arr(i): arr(i) = arr(j): arr(j) = tmp
            i = i + 1: j = j - 1
        End If
    Loop
    If lo < j Then QuickSort arr, lo, j
    If i < hi Then QuickSort arr, i, hi
End Sub
