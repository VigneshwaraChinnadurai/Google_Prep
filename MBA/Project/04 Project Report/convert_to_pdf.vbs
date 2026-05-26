Option Explicit
Dim objWord, objDoc, docPath, pdfPath

docPath = "C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Personal Work\Google_Prep\MBA\Project\04 Project Report\Project_Report.docx"
pdfPath = "C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Personal Work\Google_Prep\MBA\Project\04 Project Report\Project_Report.pdf"

Set objWord = CreateObject("Word.Application")
objWord.Visible = False
objWord.DisplayAlerts = 0
objWord.AutomationSecurity = 3 ' msoAutomationSecurityForceDisable

' Open with all dialogs suppressed
Set objDoc = objWord.Documents.Open(docPath, False, True, False, , , False, , , , , False, False, 0, True)

' Export as PDF (ExportAsFixedFormat is more reliable than SaveAs)
objDoc.ExportAsFixedFormat pdfPath, 17, False, 0, 0, , , 0, , , , , , , False

Dim pages
pages = objDoc.ComputeStatistics(2)
WScript.Echo "Pages: " & pages

objDoc.Close False
objWord.Quit
Set objDoc = Nothing
Set objWord = Nothing

WScript.Echo "PDF conversion complete!"
