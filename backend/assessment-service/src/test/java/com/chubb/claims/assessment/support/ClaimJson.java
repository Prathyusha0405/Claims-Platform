package com.chubb.claims.assessment.support;

/** Canned claim-service response bodies, shaped exactly like claim-service's real JSON. */
public final class ClaimJson {

    private ClaimJson() {
    }

    public static String claim(long id, String status, Long staffId) {
        return claim(id, status, staffId, "2500.00");
    }

    public static String claim(long id, String status, Long staffId, String amount) {
        return """
                {"id":%d,"claimNumber":"CLM-2026-%06d","policyNumber":"POL-2026-1","claimType":"AUTO","market":"SG","status":"%s",
                 "claimAmount":%s,"currency":"SGD","incidentDate":"2026-10-01","assignedStaffId":%s,
                 "createdAt":"2026-10-22T06:00:00Z","updatedAt":"2026-10-22T06:30:00Z"}"""
                .formatted(id, id, status, amount, staffId == null ? "null" : staffId.toString());
    }

    public static String page(String... claims) {
        return """
                {"content":[%s],"page":0,"size":20,"totalElements":%d,"totalPages":1,"first":true,"last":true}"""
                .formatted(String.join(",", claims), claims.length);
    }

    public static String error(int status, String message) {
        return """
                {"timestamp":"2026-10-22T06:00:00Z","status":%d,"error":"x","message":"%s","path":"/x"}""".formatted(status, message);
    }

    /**
     * The sample statistics the dashboard tests share: 13 claims in two markets, each total in its own market's currency
     * (SGD and JPY). At the rates the tests use (SGD 1.35 and JPY 150 per USD) they convert to round USD figures:
     * 6 pending, 11 exposed (6,900.50 USD), 900.00 USD settled, 3 claims in the work queue.
     */
    public static String statistics() {
        return """
                {"byStatus":[
                  {"market":"SG","currency":"SGD","status":"SUBMITTED","claimCount":2,"totalAmount":270.00},
                  {"market":"JP","currency":"JPY","status":"SUBMITTED","claimCount":1,"totalAmount":15000.00},
                  {"market":"SG","currency":"SGD","status":"UNDER_REVIEW","claimCount":2,"totalAmount":2700.00},
                  {"market":"JP","currency":"JPY","status":"MORE_INFO_REQUIRED","claimCount":1,"totalAmount":75075.00},
                  {"market":"SG","currency":"SGD","status":"APPROVED","claimCount":3,"totalAmount":4050.00},
                  {"market":"JP","currency":"JPY","status":"APPROVED","claimCount":1,"totalAmount":150000.00},
                  {"market":"JP","currency":"JPY","status":"REJECTED","claimCount":1,"totalAmount":15000.00},
                  {"market":"SG","currency":"SGD","status":"SETTLED","claimCount":2,"totalAmount":1215.00}],
                 "byStaff":[
                  {"staffId":7,"market":"SG","currency":"SGD","status":"UNDER_REVIEW","claimCount":2,"totalAmount":2700.00},
                  {"staffId":7,"market":"SG","currency":"SGD","status":"APPROVED","claimCount":3,"totalAmount":4050.00},
                  {"staffId":8,"market":"JP","currency":"JPY","status":"MORE_INFO_REQUIRED","claimCount":1,"totalAmount":75075.00},
                  {"staffId":8,"market":"JP","currency":"JPY","status":"APPROVED","claimCount":1,"totalAmount":150000.00},
                  {"staffId":8,"market":"JP","currency":"JPY","status":"REJECTED","claimCount":1,"totalAmount":15000.00},
                  {"staffId":9,"market":"SG","currency":"SGD","status":"SETTLED","claimCount":2,"totalAmount":1215.00}]}""";
    }
}
