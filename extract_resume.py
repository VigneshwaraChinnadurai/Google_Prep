from PyPDF2 import PdfReader

reader = PdfReader(r'C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Personal Work\Google_Prep\Resume\Vigneshwara_Chinnadurai_Apr_2026.pdf')
for page in reader.pages:
    print(page.extract_text())
