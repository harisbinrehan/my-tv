#!/usr/bin/env python3
"""Builds app/src/main/assets/channel_logos.json: normalised channel name -> logo address.

Logos come from the public iptv-org database (https://github.com/iptv-org/database, public domain). The map
covers PTCL Shoq's line-up (SHOQ_NAMES, from the published channel names) and every Pakistani channel, with
Pakistani channels winning any name clash. The app downloads each logo once and keeps it.

Run from the repository root:  python3 tools/channel_logos.py
"""
import json
import re
import urllib.request

API = "https://iptv-org.github.io/api"
OUT = "app/src/main/assets/channel_logos.json"

SHOQ_NAMES = """
PTV News|Geo News|Samaa News HD|Dawn News|24 Channel|Hum News HD|Dunya News|ARY News HD|Express News|Aaj News|PNN|Neo TV|ABN News|Public TV|GNN|GTV|Roze News|92 News|AbbTakk News|Sach News|Suno TV|Capital TV|Geo Taiz|News One|City 42|Channel 5|Bol News|TV Today|Voice News|Talon News
ARY Digital HD|Hum TV HD|Geo Entertainment|Urdu 1|Green Entertainment|PTV Home|AAN TV|Geo Kahani|Bol Entertainment|Express Entertainment|A Plus|Aaj Entertainment|Filmax|Hum Sitaray|ARY Zindagi|Film World|LTN TV|Play Entertainment|Raavi TV|A TV|C TV|A1 TV|Sab TV|Aur Life|Alif TV|Mun TV|Aruj TV|TV One|Lahore Rang|Kohenoor|Kay 2|Venus TV|Hum Masala|8XM|Jalwa|ARY Muziq|MTV HD|Discover Pakistan|Venus HD|In-Plus TV|Special TV
Food Network HD|PTV Teleschool
Kids Zone +|Cartoon Network|Nickelodeon|Planet Fun|Baby TV|MiniMax|Hoora TV|Sky Cartoonito|Display Junior HD|BBC CBeebies HD
CGTN Documentary|National Geographic HD|Nat Geo People HD|Nat Geo Wild|BBC Earth|BBC Lifestyle|Discovery HD|Discovery ID HD|Discovery Life HD|Discovery Science|DMax HD|Animal Planet HD|Love Nature|Home and Garden TV|History Channel|Travel Channel|DocuBox HD|Outdoor Channel HD
Fast & Fun Box HD|PTV Sports HD|Ten Sports|A Sports HD|Geo Super|Fast Sports|Golf Channel|EuroSports 1|EuroSports 2 HD|Motorvision TV|beIN Sports 1 HD|beIN Sports|beIN Sports 3 HD|AD Sports 1 HD|SuperSport Tennis|SuperSport Cricket HD|SuperSport Action HD|SuperSport Football HD|SuperSport 3 HD|SuperSport 2 HD|SuperSport 4 HD|Extreme Sports HD|DTX HD|Turbo Extra HD|Motor Trend HD|Flight Box HD
MBC Max|MBC Action|MBC 2|HBO|HBO 2|HBO Signature HD|HBO Family HD|Warner TV HD|Paramount Network HD|Comedy Central HD|Fox|BBC Brit|BBC First|Sci-Fi|AXN HD|AXN Spin HD|OSN Premier|OSN Comedy|OSN Movies|OSN TV Crime HD|Sundance TV HD|13th Street
PTV World|Al Jazeera|CNN|BBC News|RT News|CGTN|DW English|Ocean News TV|Bloomberg|NHK World|TRT World HD|Sky News HD|Press TV HD|Euronews HD
PTV Bolan|PTV Global|PTV National|Lahore News|City 21|City 41|Metro One|7 News|K21|Star Asia|Satellite TV|Rohi|Apna Channel|VHS HD|Waseeb|Khyber TV|Khyber News|Pashto One|Afghan TV|Mashriq TV|KTN|KTN News|Kashish|Sindh TV News|Sindh TV|Mehran TV|Awaz TV|Dharti TV|Time News|Roz Excel HD|Paigham TV
Al Quran Al Kareem|Hadi TV HD|QTV|Peace TV|Al Sunnah Al Nabawiyah
"""

# Shoq names that the database spells differently.
ALIASES = {
    "Samaa News HD": "SamaaTV.pk", "24 Channel": "24NewsHD.pk", "AbbTakk News": "AbbTakk.pk", "Geo Taiz": "GeoTez.pk",
    "LTN TV": "LTNFamily.pk", "ARY Muziq": "ARYMusik.pk", "Kids Zone +": "KidsZone.pk", "Sky Cartoonito": "Cartoonito.uk",
    "BBC CBeebies HD": "CBeebies.au", "Discovery ID HD": "InvestigationDiscovery.au", "EuroSports 1": "Eurosport1.fr",
    "EuroSports 2 HD": "Eurosport2.fr", "Extreme Sports HD": "ExtremeSportsChannel.nl", "DTX HD": "DTX.pl",
    "OSN Premier": "OSNMoviesPremiere.ae", "DW English": "DW.de", "Pashto One": "Pashto1.pk", "Awaz TV": "AwazTVNetwork.pk",
    "Paigham TV": "PaighamTelevision.pk", "Hadi TV HD": "HadiTV.uk", "Peace TV": "PeaceTVEnglish.ae", "Ten Sports": "TenSportsPakistan.pk",
}

COUNTRY_RANK = {"PK": 0, "AE": 1, "QA": 1, "SA": 1, "GB": 2, "UK": 2, "US": 3}


def norm(name):
    """Same rule as normaliseChannelName in ChannelLogos.kt."""
    text = re.sub(r"[^a-z0-9]+", " ", name.lower().replace("&", " and ").replace("+", " plus "))
    return "".join(word for word in text.split() if word not in ("hd", "tv", "the"))


def fetch(name):
    with urllib.request.urlopen(f"{API}/{name}.json", timeout=60) as response:
        return json.load(response)


def main():
    channels, logos = fetch("channels"), fetch("logos")
    best = {}
    for logo in logos:
        if logo.get("feed") or (logo.get("format") or "").upper() == "SVG" or not logo["url"].startswith("https://"):
            continue
        current = best.get(logo["channel"])
        if current is None or (logo.get("width") or 0) > (current.get("width") or 0):
            best[logo["channel"]] = logo
    index = {}
    for channel in channels:
        if channel["id"] not in best:
            continue
        # A channel the database lists as closed may still be on Shoq; it only fills gaps.
        rank = COUNTRY_RANK.get(channel.get("country"), 5) + (10 if channel.get("closed") else 0)
        for name in [channel["name"]] + (channel.get("alt_names") or []):
            keys = {norm(name)}
            if channel.get("country") == "PK":
                keys.add(norm(name).removesuffix("pakistan"))
            for key in filter(None, keys):
                if key not in index or rank < index[key][0]:
                    index[key] = (rank, best[channel["id"]]["url"])
    names = [n.strip() for line in SHOQ_NAMES.strip().splitlines() for n in line.split("|") if n.strip()]
    out = {key: url for key, (rank, url) in index.items() if rank in (0, 10)}
    for name in names:
        key = norm(name)
        if key in index:
            out.setdefault(key, index[key][1])
        elif index.get(key + "news", (99,))[0] in (0, 10):
            # Shoq's "Suno TV" or "K21" is the database's Pakistani "Suno News HD" or "K21 News".
            out[key] = index[key + "news"][1]
    for name, channel_id in ALIASES.items():
        if channel_id in best:
            out[norm(name)] = best[channel_id]["url"]
    with open(OUT, "w") as f:
        json.dump(out, f, separators=(",", ":"), sort_keys=True)
    missing = [n for n in names if norm(n) not in out]
    print(f"{len(out)} names mapped; Shoq channels with a logo: {len(names) - len(missing)} of {len(names)}")
    print("no logo:", ", ".join(missing))


if __name__ == "__main__":
    main()
