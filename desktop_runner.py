import os
import sys
import webview

def main():
    # Get absolute path to the HTML file
    if getattr(sys, 'frozen', False):
        # Running in a PyInstaller bundle
        base_path = sys._MEIPASS
    else:
        # Running in normal Python environment
        base_path = os.path.dirname(os.path.abspath(__file__))
    
    html_path = os.path.join(base_path, 'design-demos', 'raksetu_app.html')
    
    if not os.path.exists(html_path):
        # Fallback if run from parent dir
        html_path = os.path.join(base_path, 'raksetu_app.html')

    # Start the webview window
    webview.create_window(
        title='SafeShield - Your Digital Safety Companion',
        url=html_path,
        width=450,
        height=850,
        resizable=True,
        min_size=(360, 640)
    )
    webview.start()

if __name__ == '__main__':
    main()
