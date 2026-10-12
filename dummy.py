from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.set_content("<html><body><h1>Dummy Screenshot for Flutter</h1></body></html>")
    page.screenshot(path="dummy.png")
    browser.close()
