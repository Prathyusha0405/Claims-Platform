-- =====================================================================================================================
-- CHUBB APAC CLAIMS PLATFORM  |  AUTH SERVICE  |  V2__seed_data.sql   (DEMO DATA)
-- ---------------------------------------------------------------------------------------------------------------------
-- 26 users: 1 MANAGER, 5 CLAIMS STAFF, 20 CLAIMANTS across Australia, Singapore, Hong Kong, Japan, Malaysia, New Zealand.
-- Data is anchored at 2026-10-22; all timestamps are UTC.
--
-- DEMO LOGINS (BCrypt-hashed below; these are public demo passwords, never use them outside a demo):
--     manager@chubb.com      / Manager@123       MANAGER      (the dashboards)
--     staff@chubb.com    / Staff@123     CLAIMS_STAFF    (work queue and decisions); the other claims staff use the same password
--     <any claimant email> / Claimant@123    CLAIMANT   (e.g. weiling.tan@example.com)
--
-- ID CONTRACT with the other services (they reference users by id only):
--     1 = manager, 2..6 = claims staff, 7..26 = claimants.  claim-service and assessment-service seed data use these ids.
--
-- To start with an EMPTY platform instead, skip this file: set FLYWAY_TARGET=1 (spring.flyway.target).
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- Staff: 1 manager and 5 claims staff (one per region; Sydney also covers New Zealand)
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO users (id, full_name, email, password_hash, role, created_at, updated_at) VALUES
    (1, 'Natalie Cheung', 'manager@chubb.com', '$2a$10$i6UhhRJ62UUY2xxhbnc.XeWMVYI0ofpO3MfLvLE7EOijnj.qIgzN.', 'MANAGER', '2025-09-05 13:32:31+00:00', '2025-09-05 13:32:31+00:00'),  -- MANAGER | Singapore
    (2, 'Olivia Bennett', 'staff@chubb.com', '$2a$10$LuCf.Ux7ySQrU9LGJ1kBF.h3FmDPct9WJUquyWmXDXIYOiyO15hBm', 'CLAIMS_STAFF', '2025-08-15 18:43:08+00:00', '2025-08-15 18:43:08+00:00'),  -- CLAIMS_STAFF | Sydney
    (3, 'Kenji Sato', 'kenji.sato@chubb.com', '$2a$10$Uo11eF53O9hCOZ4/bA.5nOgQv79FwgS2mOUr0n39NpD5Zj5dPyXUK', 'CLAIMS_STAFF', '2025-08-03 05:57:04+00:00', '2025-08-03 05:57:04+00:00'),  -- CLAIMS_STAFF | Tokyo
    (4, 'Mei Lin Chua', 'mei.lin.chua@chubb.com', '$2a$10$Or6UA4pRbmQoJOJxrpVqU.Glq1mdRDjDg1bAH62xUHcSez0h6bYD.', 'CLAIMS_STAFF', '2025-08-22 14:55:14+00:00', '2025-08-22 14:55:14+00:00'),  -- CLAIMS_STAFF | Singapore
    (5, 'Rajesh Kumar', 'rajesh.kumar@chubb.com', '$2a$10$RDPUiGPDhXS/eFQTd..q3elznAP6YCrSbvQKuOMBBevY9l5b9b5.i', 'CLAIMS_STAFF', '2025-07-07 16:37:18+00:00', '2025-07-07 16:37:18+00:00'),  -- CLAIMS_STAFF | Kuala Lumpur
    (6, 'Grace Wong', 'grace.wong@chubb.com', '$2a$10$8zh4yzYaer/jj9NXed7ybeKrtx3b7hc8dhYrEBKgzCRc3qvQ1XRAG', 'CLAIMS_STAFF', '2025-08-02 09:54:48+00:00', '2025-08-02 09:54:48+00:00');  -- CLAIMS_STAFF | Hong Kong

-- ---------------------------------------------------------------------------------------------------------------------
-- Claimants: 20 policyholders (AU 5, SG 4, HK 3, JP 3, MY 3, NZ 2); each registered before their first claim
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO users (id, full_name, email, password_hash, role, created_at, updated_at) VALUES
    (7, 'Liam O''Connor', 'liam.oconnor@example.com', '$2a$10$5woXnJvF6DQ2pleTLldp3OPedLNc539.wKh3vxL4rqLEPWFCx6tmO', 'CLAIMANT', '2025-12-10 16:54:11+00:00', '2025-12-10 16:54:11+00:00'),  -- CLAIMANT | Australia | 14 claims
    (8, 'Charlotte Nguyen', 'charlotte.nguyen@example.com', '$2a$10$JO/y14Lm08otxElhrPmCkOcIWFYAbVb5JpZadZvfC2aMjUknIQ.W2', 'CLAIMANT', '2026-03-01 16:07:06+00:00', '2026-03-01 16:07:06+00:00'),  -- CLAIMANT | Australia | 14 claims
    (9, 'Jack Thompson', 'jack.thompson@example.com', '$2a$10$xswUnTFZvdkeCrVt.xZ80.ZXBluBMOsuWqXK/Fo0p6BoUe6Ac406m', 'CLAIMANT', '2025-09-28 01:13:11+00:00', '2025-09-28 01:13:11+00:00'),  -- CLAIMANT | Australia | 14 claims
    (10, 'Isla Patel', 'isla.patel@example.com', '$2a$10$gERUGbwSf1mOOUyZl2PvTeo/CJmDB82ZFdvG7D6LElC9j9jjD2SyK', 'CLAIMANT', '2025-11-19 13:25:46+00:00', '2025-11-19 13:25:46+00:00'),  -- CLAIMANT | Australia | 14 claims
    (11, 'Mitchell Brown', 'mitchell.brown@example.com', '$2a$10$Pv9expcF1JxpARcJIpt7veuduHdlNQcZrmWEXR.uM4vqqnsJBXKAC', 'CLAIMANT', '2026-01-28 05:19:14+00:00', '2026-01-28 05:19:14+00:00'),  -- CLAIMANT | Australia | 14 claims
    (12, 'Wei Ling Tan', 'weiling.tan@example.com', '$2a$10$teMAHmHzfGRUmncK6fHXD.YAr/pozGUufiVlQ5pktjCZ0FFzpPxOy', 'CLAIMANT', '2026-01-09 22:31:59+00:00', '2026-01-09 22:31:59+00:00'),  -- CLAIMANT | Singapore | 13 claims
    (13, 'Arun Krishnan', 'arun.krishnan@example.com', '$2a$10$FPIvoprb1sv0uWpVFfLz.Oz2EVSO7RcXA2/eCuCQ0JLUtDh0AzrMW', 'CLAIMANT', '2025-11-19 04:55:19+00:00', '2025-11-19 04:55:19+00:00'),  -- CLAIMANT | Singapore | 13 claims
    (14, 'Nurul Aini Rahman', 'nurul.aini@example.com', '$2a$10$C7MlXfdjM45b/Vhtuk2OquVBxC/ecFrjQhz0wN3g/x49kbeJTRmCS', 'CLAIMANT', '2025-12-22 04:41:34+00:00', '2025-12-22 04:41:34+00:00'),  -- CLAIMANT | Singapore | 12 claims
    (15, 'Jason Lim', 'jason.lim@example.com', '$2a$10$t2bpydW4Flj3Siu0RlOZCeOuLPZM3srjjbqBI9FdIj5GRT6mIh1CC', 'CLAIMANT', '2026-05-16 19:44:59+00:00', '2026-05-16 19:44:59+00:00'),  -- CLAIMANT | Singapore | 12 claims
    (16, 'Chan Tai Man', 'taiman.chan@example.com', '$2a$10$rP2OexjfmxdUAF2bWCJOkONXYH5Eg85Z0KEzVwSYYoW94sIMygxWW', 'CLAIMANT', '2025-12-10 12:09:25+00:00', '2025-12-10 12:09:25+00:00'),  -- CLAIMANT | Hong Kong | 10 claims
    (17, 'Wong Mei Yee', 'meiyee.wong@example.com', '$2a$10$uzMX6EkP2F.X/u4TzgdO0Ovoma.XmoGHGnd.0yZn98sayQzkhoHDS', 'CLAIMANT', '2025-10-09 03:12:17+00:00', '2025-10-09 03:12:17+00:00'),  -- CLAIMANT | Hong Kong | 10 claims
    (18, 'Leung Ka Ho', 'kaho.leung@example.com', '$2a$10$VTLbu1QBbOCxkw4eaWgKlemcxjQHEK4XOWnaiJ5jlozAeksGmSPcG', 'CLAIMANT', '2025-12-03 03:28:26+00:00', '2025-12-03 03:28:26+00:00'),  -- CLAIMANT | Hong Kong | 10 claims
    (19, 'Hiroshi Tanaka', 'hiroshi.tanaka@example.com', '$2a$10$fSlyAF6vWr8jT7vu1ClByumRr0cHlmnoakvovKbnD5WfC9HpO/DDq', 'CLAIMANT', '2026-03-01 10:18:44+00:00', '2026-03-01 10:18:44+00:00'),  -- CLAIMANT | Japan | 13 claims
    (20, 'Yuki Nakamura', 'yuki.nakamura@example.com', '$2a$10$2Ls57uouakebFWyvXtF08.QigCpT13tUB0tInldt9EtEc8YzEiXtq', 'CLAIMANT', '2025-12-02 11:42:55+00:00', '2025-12-02 11:42:55+00:00'),  -- CLAIMANT | Japan | 14 claims
    (21, 'Aiko Yamamoto', 'aiko.yamamoto@example.com', '$2a$10$5mSwyKpQmsoh7SUIYMsagebvCGO11hggnS8t4eWSCJ5Vm6h9Zsvd6', 'CLAIMANT', '2025-12-03 11:06:20+00:00', '2025-12-03 11:06:20+00:00'),  -- CLAIMANT | Japan | 13 claims
    (22, 'Ahmad Faizal Abdullah', 'ahmad.faizal@example.com', '$2a$10$KmMwb6V94iQS.7cMFvRVzO1UFrnpHSxF7VMDg/hpxCQk6lbfDbNG.', 'CLAIMANT', '2025-12-27 02:32:28+00:00', '2025-12-27 02:32:28+00:00'),  -- CLAIMANT | Malaysia | 12 claims
    (23, 'Lim Soo Ching', 'soo.ching.lim@example.com', '$2a$10$oDVE3nbbrOMiVxtLXhYwXenib/aw3eN01BxzYglJA/O4xVe9GdpZC', 'CLAIMANT', '2026-02-05 13:03:11+00:00', '2026-02-05 13:03:11+00:00'),  -- CLAIMANT | Malaysia | 12 claims
    (24, 'Priya Devi Subramaniam', 'priya.devi@example.com', '$2a$10$ffXq/Wy7LLhemgGPuG.WoONYCUA9FQq9G6JYenG1jt/IPvI1DZOe.', 'CLAIMANT', '2026-01-30 13:40:42+00:00', '2026-01-30 13:40:42+00:00'),  -- CLAIMANT | Malaysia | 11 claims
    (25, 'Aroha Ngata', 'aroha.ngata@example.com', '$2a$10$KM0Vl0edllmTyYQAxQlfvOLJ84iYbhTGJ0Ale/xAkIg4LN2NecRj6', 'CLAIMANT', '2025-09-25 18:55:34+00:00', '2025-09-25 18:55:34+00:00'),  -- CLAIMANT | New Zealand | 12 claims
    (26, 'Callum Henderson', 'callum.henderson@example.com', '$2a$10$QeXidzCoLdEpv08G8nspkeW0mboTvm0R/ptQloHn0fiX2ewMUXH12', 'CLAIMANT', '2025-11-10 14:32:12+00:00', '2025-11-10 14:32:12+00:00');  -- CLAIMANT | New Zealand | 13 claims

-- ---------------------------------------------------------------------------------------------------------------------
-- The ids above were inserted explicitly, so move the identity counter past them: the next registration gets id 27.
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN id RESTART WITH 27;
