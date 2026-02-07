#!/usr/bin/env python3
"""
Scrapes drop tables from the OSRS Wiki for all collection log sources.
Outputs JSON files for use by the Droppy RuneLite plugin.
"""

import json
import os
import re
import time
import urllib.parse
import requests

WIKI_API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "Droppy Drop Data Scraper (https://github.com/bckpockets/droppy)"
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")

# All collection log pages
CLOG_PAGES = [
    # Bosses
    "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Barrows Chests",
    "Bryophyta", "Callisto", "Artio", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
    "Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist", "Dagannoth Kings",
    "Deranged Archaeologist", "Duke Sucellus", "General Graardor", "Giant Mole",
    "Grotesque Guardians", "Hespori", "Hueycoatl", "Kalphite Queen", "King Black Dragon",
    "Kraken", "Kree'arra", "K'ril Tsutsaroth", "Leviathan", "Mimic", "Nex",
    "Nightmare", "Obor", "Phantom Muspah", "Royal Titans", "Sarachnis", "Scorpia",
    "Scurrius", "Skotizo", "Sol Heredit", "Spindel",
    "Thermonuclear Smoke Devil", "Tormented Demons", "Vardorvis", "Venenatis",
    "Vet'ion", "Calvar'ion", "Vorkath", "Whisperer", "Wintertodt", "Zalcano", "Zulrah",
    # Raids
    "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut",
    # Clues - use reward casket pages
    "Reward casket (beginner)", "Reward casket (easy)", "Reward casket (medium)",
    "Reward casket (hard)", "Reward casket (elite)", "Reward casket (master)",
    # Minigames
    "Barbarian Assault", "Brimhaven Agility Arena", "Castle Wars", "Creature Creation",
    "Fishing Trawler", "Gnome Restaurant", "Guardians of the Rift", "Hallowed Sepulchre",
    "Last Man Standing", "Magic Training Arena", "Mahogany Homes", "Pest Control",
    "Pyramid Plunder", "Rogues' Den", "Shades of Mort'ton", "Soul Wars",
    "Tai Bwo Wannai Cleanup", "Temple Trekking", "Tithe Farm", "Trouble Brewing",
    "Volcanic Mine",
    # Other
    "Aerial Fishing", "Champion's Challenge", "Chaos Druids",
    "Chompy Bird Hunting", "Colosseum", "Cyclopes", "Defenders of Varrock",
    "Glough's Experiments", "Gorak", "Revenants", "Slayer", "TzHaar", "Undead Druids",
    # Gauntlet
    "The Gauntlet",
]

# Page aliases - in-game names to wiki page names
PAGE_ALIASES = {
    "callisto and artio": ["Callisto", "Artio"],
    "venenatis and spindel": ["Venenatis", "Spindel"],
    "vet'ion and calvar'ion": ["Vet'ion", "Calvar'ion"],
    "beginner treasure trails": "Reward casket (beginner)",
    "easy treasure trails": "Reward casket (easy)",
    "medium treasure trails": "Reward casket (medium)",
    "hard treasure trails": "Reward casket (hard)",
    "elite treasure trails": "Reward casket (elite)",
    "master treasure trails": "Reward casket (master)",
    "the corrupted gauntlet": "The Gauntlet",
    "chambers of xeric: challenge mode": "Chambers of Xeric",
    "theatre of blood: hard mode": "Theatre of Blood",
    "tombs of amascut: expert mode": "Tombs of Amascut",
}

# Subpages to try when main page has no drops
SUBPAGE_SUFFIXES = ["/Loot", "/Rewards", "/Drop rates"]

# Rarity keywords
RARITY_KEYWORDS = {
    "always": 1.0,
    "common": 1/16,
    "uncommon": 1/64,
    "rare": 1/128,
    "very rare": 1/512,
}

DROPS_LINE_PATTERN = re.compile(r'\{\{DropsLine\s*\|([^}]+)\}\}', re.IGNORECASE)
FRACTION_PATTERN = re.compile(r'(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)')


def fetch_wiki_text(page_name):
    """Fetch wikitext for a page from the OSRS Wiki API."""
    params = {
        "action": "parse",
        "page": page_name,
        "prop": "wikitext",
        "format": "json",
    }
    headers = {"User-Agent": USER_AGENT}

    try:
        resp = requests.get(WIKI_API, params=params, headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        if "error" in data:
            return None

        return data.get("parse", {}).get("wikitext", {}).get("*", "")
    except Exception as e:
        print(f"  Error fetching {page_name}: {e}")
        return None


def parse_rarity(rarity_str):
    """Parse a rarity string into a decimal probability."""
    if not rarity_str:
        return -1

    # Clean up the string
    cleaned = rarity_str.strip()
    cleaned = re.sub(r'<!--.*?-->', '', cleaned)
    cleaned = re.sub(r'\{\{.*?\}\}', '', cleaned)
    cleaned = cleaned.replace('~', '').strip()

    # Try to match a fraction
    match = FRACTION_PATTERN.search(cleaned)
    if match:
        num = float(match.group(1))
        den = float(match.group(2))
        if den > 0:
            return num / den

    # Try rarity keywords
    lower = cleaned.lower()
    for keyword, value in RARITY_KEYWORDS.items():
        if keyword in lower:
            return value

    return -1


def format_rarity_display(rarity_str, drop_rate):
    """Format drop rate for display."""
    if not rarity_str:
        denom = round(1.0 / drop_rate)
        return f"1/{denom:,}"

    cleaned = rarity_str.strip()
    cleaned = re.sub(r'<!--.*?-->', '', cleaned)
    cleaned = re.sub(r'\{\{.*?\}\}', '', cleaned)
    cleaned = cleaned.replace('~', '').strip()

    match = FRACTION_PATTERN.search(cleaned)
    if match:
        num = float(match.group(1))
        den = float(match.group(2))

        if num == int(num) and den == int(den):
            n, d = int(num), int(den)
            # Keep original format for small numerators
            if n <= 10:
                return f"{n:,}/{d:,}"
            # Simplify large numerators
            simple_denom = round(1.0 / drop_rate)
            return f"1/{simple_denom:,}"

    denom = round(1.0 / drop_rate)
    return f"1/{denom:,}"


def parse_template_params(param_string):
    """Parse template parameters handling nested templates."""
    params = {}
    depth = 0
    current = []

    for char in param_string:
        if char == '{':
            depth += 1
            current.append(char)
        elif char == '}':
            depth -= 1
            current.append(char)
        elif char == '|' and depth == 0:
            param = ''.join(current).strip()
            if '=' in param:
                key, value = param.split('=', 1)
                params[key.strip().lower()] = value.strip()
            current = []
        else:
            current.append(char)

    # Handle last param
    if current:
        param = ''.join(current).strip()
        if '=' in param:
            key, value = param.split('=', 1)
            params[key.strip().lower()] = value.strip()

    return params


def parse_drops_from_wikitext(wikitext):
    """Parse DropsLine templates from wikitext."""
    drops = []

    for match in DROPS_LINE_PATTERN.finditer(wikitext):
        params = parse_template_params(match.group(1))

        name = params.get("name", "").strip()
        rarity = params.get("rarity", "")

        if not name:
            continue

        drop_rate = parse_rarity(rarity)
        if drop_rate <= 0 or drop_rate >= 1.0:
            continue

        rarity_display = format_rarity_display(rarity, drop_rate)

        item_id = -1
        if "id" in params:
            try:
                item_id = int(params["id"].strip())
            except ValueError:
                pass

        drops.append({
            "name": name,
            "rate": drop_rate,
            "rateDisplay": rarity_display,
            "itemId": item_id,
        })

    return drops


def fetch_drops_for_page(page_name):
    """Fetch and parse drops for a page, trying subpages if needed."""
    wikitext = fetch_wiki_text(page_name)
    if wikitext:
        drops = parse_drops_from_wikitext(wikitext)
        if drops:
            return drops

    # Try subpages
    for suffix in SUBPAGE_SUFFIXES:
        wikitext = fetch_wiki_text(page_name + suffix)
        if wikitext:
            drops = parse_drops_from_wikitext(wikitext)
            if drops:
                print(f"  Found drops on {page_name}{suffix}")
                return drops

    return []


def normalize_filename(name):
    """Convert page name to a safe filename."""
    # Replace special characters
    safe = name.lower()
    safe = re.sub(r'[^a-z0-9]+', '_', safe)
    safe = safe.strip('_')
    return safe


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    all_data = {}
    failed = []

    print(f"Scraping {len(CLOG_PAGES)} pages...")

    for page in CLOG_PAGES:
        print(f"Fetching: {page}")
        drops = fetch_drops_for_page(page)

        if drops:
            filename = normalize_filename(page)
            all_data[filename] = {
                "name": page,
                "drops": drops,
            }
            print(f"  Found {len(drops)} drops")
        else:
            failed.append(page)
            print(f"  No drops found")

        # Rate limit
        time.sleep(0.5)

    # Write individual files
    for filename, data in all_data.items():
        filepath = os.path.join(OUTPUT_DIR, f"{filename}.json")
        with open(filepath, 'w') as f:
            json.dump(data, f, indent=2)

    # Write index file with all data
    index_path = os.path.join(OUTPUT_DIR, "index.json")
    with open(index_path, 'w') as f:
        json.dump({
            "sources": list(all_data.keys()),
            "aliases": {k: v if isinstance(v, str) else v[0] for k, v in PAGE_ALIASES.items()},
        }, f, indent=2)

    # Write combined file for single fetch
    all_path = os.path.join(OUTPUT_DIR, "all.json")
    with open(all_path, 'w') as f:
        json.dump(all_data, f)

    print(f"\nDone! Wrote {len(all_data)} files to {OUTPUT_DIR}")
    if failed:
        print(f"Failed pages: {failed}")


if __name__ == "__main__":
    main()
