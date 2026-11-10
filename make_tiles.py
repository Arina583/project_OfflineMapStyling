import io, math, sqlite3, time, requests
from PIL import Image  # только для проверки валидности PNG

BBOX = (56.08, 40.30, 56.18, 40.50)   # lat_min, lon_min, lat_max, lon_max — Владимир
ZOOMS = range(13, 17)
SUBS = ["a", "b", "c", "d"]
ATTRIBUTION = "© OpenStreetMap contributors, © CARTO"
# два стиля одной территории: светлый Positron и тёмный Dark Matter
STYLES = {"light_all": "light.mbtiles", "dark_all": "dark.mbtiles"}

def deg2num(lat, lon, z):
    """Перевод градусы -> XYZ (нумерация OSM, сверху вниз)."""
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_r = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2 * n)
    return x, min(y, n - 1)

def init_db(path, style):
    db = sqlite3.connect(path)
    db.execute("CREATE TABLE IF NOT EXISTS metadata "
               "(name TEXT PRIMARY KEY, value TEXT)")
    db.execute("""CREATE TABLE IF NOT EXISTS tiles (
        zoom_level INTEGER NOT NULL, tile_column INTEGER NOT NULL,
        tile_row INTEGER NOT NULL, tile_data BLOB NOT NULL,
        UNIQUE (zoom_level, tile_column, tile_row))""")
    for k, v in [("name", f"vladimir-{style}"), ("format", "png"),
                 ("type", "baselayer"), ("version", "1.0.0"),
                 ("minzoom", str(min(ZOOMS))), ("maxzoom", str(max(ZOOMS))),
                 ("bounds", f"{BBOX[1]},{BBOX[0]},{BBOX[3]},{BBOX[2]}"),
                 ("attribution", ATTRIBUTION)]:
        db.execute("INSERT OR REPLACE INTO metadata VALUES (?, ?)", (k, v))
    return db

def download(style, out_path):
    db = init_db(out_path, style)
    total, errors = 0, 0
    for z in ZOOMS:
        x0, y0 = deg2num(BBOX[2], BBOX[1], z)   # левый верхний угол
        x1, y1 = deg2num(BBOX[0], BBOX[3], z)   # правый нижний
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                s = SUBS[total % len(SUBS)]
                url = (f"https://{s}.basemaps.cartocdn.com/"
                       f"rastertiles/{style}/{z}/{x}/{y}.png?key=КЛЮЧ")
                try:
                    r = requests.get(url, timeout=10,
                        headers={"User-Agent": "VlGU-MobileGIS-Lab5/1.0"})
                    r.raise_for_status()
                    Image.open(io.BytesIO(r.content)).verify()  # PNG валиден
                except Exception:
                    errors += 1
                    continue
                y_tms = (2 ** z - 1) - y          # XYZ -> TMS для MBTiles!
                db.execute("INSERT OR IGNORE INTO tiles VALUES (?,?,?,?)",
                           (z, x, y_tms, r.content))
                db.commit()
                total += 1
                time.sleep(0.25)                  # вежливость к сервису
        print(f"зум {z}: готово")
    print(f"{style}: скачано {total}, ошибок {errors}")

for style, path in STYLES.items():
    download(style, path)
