import re
import subprocess
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.shared import Pt, Inches

# Project files

JAR = r"C:\Users\HP\Downloads\TLAToolbox-1.8.0-win32.win32.x86_64\toolbox\tla2tools.jar"

TLC_MODEL = "TwoPCBankSoec"
TLC_CONFIG = "twopcbankSpec_MC"

TRACE_MODEL = "2pcBankTraceSpec"
TRACE_CONFIG = "model_1_2pcBankTraceSpec"

# Run TLC automatically

def run_tlc(model, config):
    result = subprocess.run(
        ["java", "-jar", JAR, "-config", config, model],
        capture_output=True,
        text=True
    )

    return result.stdout + result.stderr

# Run normal TLC model checking

tlc_output = run_tlc(TLC_MODEL, TLC_CONFIG)

# Run trace validation

trace_output = run_tlc(TRACE_MODEL, TRACE_CONFIG)

# Extract TLC results from the console

generated = re.search(r"(\d+) states generated", tlc_output, re.ignorecase)
distinct = re.search(r"(\d+) distinct states", tlc_output, re.ignorecase)
queue = re.search(r"(\d+) states left on queue", tlc_output, re.ignorecase)
time = re.search(
    r"(?:model checking time)\s*[:=]\s*"
    r"([0-9]+(?:\.[0-9]+)?)\s*(seconds?|sec|s)"
, tlc_output, re.ignorecase)
deadlock = re.search(r”(\d+) deadlocks” tlc_output, re.ignorecase)
safety = re.search(r”(\d+) safety violations”, tlc_output, re.ignorecase)

# Extract trace-validation results from the console

trace_length = re.search(
    r"trace length\s*:?\s*(\d+)",
    trace_output,
    re.ignorecase
)

trace_accepted = re.search(
    r"trace atccepted\s*:?\s*(Yes|No)",
    trace_output,
    re.ignorecase
)
trace_validation_status = re.search(
	r”trace validation status\s*:?\s*(pass|fail)”,
	trace_output,
	re.ignorecase
)
# CREATE  OR OPEN tlcresult.txt and traceresult.txt
with open("tlc_result", "w", encoding="utf-8") as f:
    f.write(tlc_output)

with open("trace_result", "w", encoding="utf-8") as f:
    f.write(trace_output)


# CREATE WORD TABLE

def create_table(document, rows):
    """
    Create a two-column Word table.
    """

    table = document.add_table(
        rows=1,
        cols=2
    )
    table.alignment = WD_TABLE_ALIGNMENT.CENTER

    # Header

    table.cell(0, 0).text = "metric"
    table.cell(0, 1).text = "result"

    for cell in table.rows[0].cells:

        for paragraph in cell.paragraphs:

            for run in paragraph.runs:
                run.bold = True
                run.font.size = Pt(12)

    # Data rows

    for metric, result in rows:

        cells = table.add_row().cells

        cells[0].text = str(metric)
        cells[1].text = str(result)

        for cell in cells:

            for paragraph in cell.paragraphs:

                for run in paragraph.runs:
                    run.font.size = Pt(12)

    return table


# CREATE WORD REPORT

def create_word_report(
        tlc_result,
        trace_result
):
    document = Document()

    # PAGE MARGINS

    for section in document.sections:
        section.top_margin = Inches(0.7)
        section.bottom_margin = Inches(0.7)
        section.left_margin = Inches(0.8)
        section.right_margin = Inches(0.8)

    # DEFAULT FONT

    style = document.styles["Normal"]
    style.font.name = "Times New Roman"
    style.font.size = Pt(12)


    tlc_rows = [

        (
            "states generated",
            tlc_result["states denerated"]
        ),

        (
            "distinct states",
            tlc_result["distinct states"]
        ),

        (
            "states left on queue",
            tlc_result["states left on queue"]
        ),

        (
            "model checking time",
            tlc_result["model checking time"]
        ),

        (
            "deadlocks",
            tlc_result["deadlocks"]
        ),

        (
            "safety violations",
            tlc_result["safety violations"]
        )
    ]

    create_table(
        document,
        tlc_rows
    )

    trace_rows = [

        (
            "trace length",
            trace_result["trace length"]
        ),

        (
            "trace accepted",
            trace_result["trace accepted"]
        ),

        (
            "trace validation status",
            trace_result["trace validation status"]
        )
    ]

    create_table(
        document,
        trace_rows
    )

    # SAVE DOCUMENT

    document.save(WORD_OUTPUT_FILE)

# MAIN PROGRAM

def main():
    print()


# START PROGRAM

if __name__ == "__main__":
    main()
