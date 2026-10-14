-- Access control of screenshots: find the scans that contain a screenshot
CREATE INDEX idx_scan_results_screenshot ON scan_results (screenshot_key);
