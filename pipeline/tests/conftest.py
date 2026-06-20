import sys
from pathlib import Path

# make `pipeline/` importable when pytest is invoked from the repo root
sys.path.insert(0, str(Path(__file__).parent.parent))
