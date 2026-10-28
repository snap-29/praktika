import json, sys, urllib.request, urllib.error
base = sys.argv[1] if len(sys.argv)>1 else "http://localhost:8080"
def request(path, method="GET", data=None, expected=200):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base+path, body, {"Content-Type":"application/json"}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            assert res.status == expected, res.status
            return json.load(res)
    except urllib.error.HTTPError as e:
        assert e.code == expected, (e.code, e.read().decode())
        return json.load(e)
request("/actuator/health/readiness")
category = request("/api/categories")[0]["id"]
t = request("/api/tickets", "POST", {"title":"Pipeline smoke", "description":"CD verification", "categoryId":category, "priority":"LOW"}, 201)
path = "/api/tickets/"+t["id"]
request(path+"/status", "PATCH", {"status":"IN_PROGRESS"})
request(path+"/status", "PATCH", {"status":"RESOLVED"})
before, history = request(path), request(path+"/history")
request(path+"/status", "PATCH", {"status":"IN_PROGRESS"},409)
assert before == request(path) and history == request(path+"/history")
report=request("/api/reports/summary?from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z")
assert report["byStatus"]["RESOLVED"] >= 1
print("PASS health, lifecycle, history, forbidden transition, report; ticket="+t["id"])
