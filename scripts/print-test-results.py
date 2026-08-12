#!/usr/bin/env python3
import glob
import os
import sys
import xml.etree.ElementTree as ET

files = sorted(glob.glob("target/surefire-reports/TEST-*.xml"))
if not files:
    print("No Surefire XML test reports found.")
    sys.exit(1)

passed = failed = skipped = 0
rows = []
for filename in files:
    root = ET.parse(filename).getroot()
    for case in root.findall(".//testcase"):
        classname = case.attrib.get("classname", "")
        method = case.attrib.get("name", "")
        short_class = classname.rsplit(".", 1)[-1]
        label = f"{short_class}#{method}"
        if case.find("failure") is not None or case.find("error") is not None:
            failed += 1
            rows.append(("FAIL", label))
        elif case.find("skipped") is not None:
            skipped += 1
            rows.append(("SKIP", label))
        else:
            passed += 1
            rows.append(("PASS", label))

print("\n=== Individual test results ===")
for status, label in rows:
    print(f"{status:4}  {label}")
print("\n=== Summary ===")
print(f"PASS: {passed}")
print(f"FAIL: {failed}")
print(f"SKIP: {skipped}")
print(f"TOTAL: {passed + failed + skipped}")

sys.exit(1 if failed else 0)
