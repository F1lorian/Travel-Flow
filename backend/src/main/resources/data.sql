INSERT INTO categories (id, name) VALUES
                                      (1, 'FOOD'),
                                      (2, 'TRANSPORT'),
                                      (3, 'HOTEL'),
                                      (4, 'LEISURE'),
                                      (5, 'OTHER')
    ON CONFLICT (id) DO NOTHING;
