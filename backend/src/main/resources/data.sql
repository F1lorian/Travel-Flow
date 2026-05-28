INSERT INTO categories (id, name) VALUES
                                      (1, 'ESSEN'),
                                      (2, 'TRANSPORT'),
                                      (3, 'HOTEL'),
                                      (4, 'FREIZEIT'),
                                      (5, 'SONSTIGES')
    ON CONFLICT (id) DO NOTHING;
