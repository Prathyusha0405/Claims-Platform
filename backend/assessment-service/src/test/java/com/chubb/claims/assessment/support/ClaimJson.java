package com.chubb.claims.assessment.support;

/** Canned claim-service response bodies, shaped exactly like claim-service's real JSON. */
public final class ClaimJson {

    private ClaimJson() {
    }

    public static String claim(long id, String status, Long officerId) {
        return claim(id, status, officerId, "2500.00");
    }

    public static String claim(long id, String status, Long officerId, String amount) {
        return """
                {"id":%d,"claimNumber":"CLM-2026-%06d","policyNumber":"POL-2026-1","claimType":"AUTO","status":"%s",
                 "claimAmount":%s,"incidentDate":"2026-10-01","assignedOfficerId":%s,
                 "createdAt":"2026-10-22T06:00:00Z","updatedAt":"2026-10-22T06:30:00Z"}"""
                .formatted(id, id, status, amount, officerId == null ? "null" : officerId.toString());
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

    /** The sample statistics the dashboard tests share: 13 claims, 6 pending, 11 exposed, 900.00 settled. */
    public static String statistics() {
        return """
                {"byStatus":[
                  {"status":"SUBMITTED","claimCount":3,"totalAmount":300.00},
                  {"status":"UNDER_REVIEW","claimCount":2,"totalAmount":2000.00},
                  {"status":"MORE_INFO_REQUIRED","claimCount":1,"totalAmount":500.50},
                  {"status":"APPROVED","claimCount":4,"totalAmount":4000.00},
                  {"status":"REJECTED","claimCount":1,"totalAmount":100.00},
                  {"status":"SETTLED","claimCount":2,"totalAmount":900.00}],
                 "byOfficer":[
                  {"officerId":7,"status":"UNDER_REVIEW","claimCount":2,"totalAmount":2000.00},
                  {"officerId":7,"status":"APPROVED","claimCount":3,"totalAmount":3000.00},
                  {"officerId":8,"status":"MORE_INFO_REQUIRED","claimCount":1,"totalAmount":500.50},
                  {"officerId":8,"status":"APPROVED","claimCount":1,"totalAmount":1000.00},
                  {"officerId":8,"status":"REJECTED","claimCount":1,"totalAmount":100.00},
                  {"officerId":9,"status":"SETTLED","claimCount":2,"totalAmount":900.00}]}""";
    }
}
