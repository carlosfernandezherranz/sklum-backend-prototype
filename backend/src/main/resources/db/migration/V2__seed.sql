-- Demo data: a small slice of a ~4,500 SKU catalog, enough to exercise search, PDP,
-- delivery promise, and checkout end to end. Warehouses match "Spain + selected EU markets".

INSERT INTO warehouses (warehouse_id, name, country) VALUES
  ('WH-MAD', 'Madrid Central', 'ES'),
  ('WH-BCN', 'Barcelona Nord', 'ES'),
  ('WH-PAR', 'Paris Sud', 'FR');

INSERT INTO products (sku, name, category, description, price_cents) VALUES
  ('SOF-1001', 'Aral 3-Seater Sofa', 'Sofas', 'Boucle-upholstered three-seat sofa with oak legs, part of the Aral living collection.', 89900),
  ('SOF-1002', 'Aral 2-Seater Sofa', 'Sofas', 'Compact two-seat version of the Aral sofa, boucle upholstery, oak legs.', 69900),
  ('SOF-1010', 'Dara Corner Sofa', 'Sofas', 'Modular corner sofa in bouclé with removable covers, left or right chaise.', 149900),
  ('SOF-1020', 'Mira Velvet Sofa', 'Sofas', 'Deep-seat velvet sofa in emerald green with brass-finish legs.', 109900),
  ('CHR-2001', 'Nora Dining Chair', 'Sillas', 'Upholstered dining chair with walnut-stained beech frame.', 12900),
  ('CHR-2002', 'Nora Armchair', 'Sillas', 'Armchair companion to the Nora dining chair, same walnut-stained frame.', 24900),
  ('CHR-2010', 'Kiva Rattan Chair', 'Sillas', 'Handwoven natural rattan accent chair with cushion.', 18900),
  ('CHR-2020', 'Teo Office Chair', 'Sillas', 'Ergonomic office chair in recycled mesh, adjustable lumbar support.', 21900),
  ('TBL-3001', 'Solis Dining Table', 'Mesas', 'Extendable oak dining table, seats 6 to 8.', 59900),
  ('TBL-3002', 'Solis Coffee Table', 'Mesas', 'Round oak coffee table matching the Solis dining collection.', 24900),
  ('TBL-3010', 'Vela Console Table', 'Mesas', 'Narrow console table in black-stained ash with brass inlay.', 34900),
  ('TBL-3020', 'Pico Side Table', 'Mesas', 'Small marble-top side table with powder-coated steel base.', 14900),
  ('LMP-4001', 'Alda Floor Lamp', 'Iluminación', 'Arc floor lamp with linen shade and marble base.', 19900),
  ('LMP-4002', 'Alda Table Lamp', 'Iluminación', 'Table lamp companion to the Alda collection, linen shade.', 8900),
  ('LMP-4010', 'Luma Pendant Light', 'Iluminación', 'Ribbed glass pendant light, brass fitting.', 11900),
  ('LMP-4020', 'Orbe Wall Sconce', 'Iluminación', 'Spherical opal-glass wall sconce, dimmable.', 7900),
  ('DEC-5001', 'Juno Wool Rug 170x240', 'Decoración', 'Hand-tufted wool rug in warm terracotta tones.', 24900),
  ('DEC-5002', 'Juno Wool Rug 240x330', 'Decoración', 'Larger format of the Juno wool rug, same terracotta tones.', 42900),
  ('DEC-5010', 'Vaso Ceramic Vase Set', 'Decoración', 'Set of three stoneware vases in matte glaze.', 5900),
  ('DEC-5020', 'Pliego Linen Cushion', 'Decoración', 'Washed-linen cushion cover, 50x50, four colourways.', 2900),
  ('OUT-6001', 'Brisa Outdoor Lounge Chair', 'Exterior', 'Weatherproof rope-woven lounge chair with aluminium frame.', 32900),
  ('OUT-6002', 'Brisa Outdoor Dining Set', 'Exterior', 'Six-seat outdoor dining table and chair set, teak finish.', 149900),
  ('OUT-6010', 'Sombra Parasol 3m', 'Exterior', 'Three-metre cantilever parasol with crank tilt mechanism.', 24900),
  ('OUT-6020', 'Sombra Outdoor Rug', 'Exterior', 'Flatwoven polypropylene outdoor rug, fade-resistant.', 8900);

-- Stock per warehouse — deliberately uneven, some SKUs low-stock or out at some warehouses
-- (feeds the delivery-promise precompute job and the "live inventory check" at checkout).
INSERT INTO inventory (sku, warehouse_id, stock_level) VALUES
  ('SOF-1001', 'WH-MAD', 14), ('SOF-1001', 'WH-BCN', 6),  ('SOF-1001', 'WH-PAR', 0),
  ('SOF-1002', 'WH-MAD', 20), ('SOF-1002', 'WH-BCN', 11), ('SOF-1002', 'WH-PAR', 9),
  ('SOF-1010', 'WH-MAD', 3),  ('SOF-1010', 'WH-BCN', 0),  ('SOF-1010', 'WH-PAR', 5),
  ('SOF-1020', 'WH-MAD', 8),  ('SOF-1020', 'WH-BCN', 8),  ('SOF-1020', 'WH-PAR', 2),
  ('CHR-2001', 'WH-MAD', 40), ('CHR-2001', 'WH-BCN', 35), ('CHR-2001', 'WH-PAR', 18),
  ('CHR-2002', 'WH-MAD', 22), ('CHR-2002', 'WH-BCN', 14), ('CHR-2002', 'WH-PAR', 0),
  ('CHR-2010', 'WH-MAD', 9),  ('CHR-2010', 'WH-BCN', 0),  ('CHR-2010', 'WH-PAR', 4),
  ('CHR-2020', 'WH-MAD', 17), ('CHR-2020', 'WH-BCN', 12), ('CHR-2020', 'WH-PAR', 10),
  ('TBL-3001', 'WH-MAD', 6),  ('TBL-3001', 'WH-BCN', 2),  ('TBL-3001', 'WH-PAR', 0),
  ('TBL-3002', 'WH-MAD', 15), ('TBL-3002', 'WH-BCN', 9),  ('TBL-3002', 'WH-PAR', 7),
  ('TBL-3010', 'WH-MAD', 5),  ('TBL-3010', 'WH-BCN', 5),  ('TBL-3010', 'WH-PAR', 0),
  ('TBL-3020', 'WH-MAD', 26), ('TBL-3020', 'WH-BCN', 19), ('TBL-3020', 'WH-PAR', 13),
  ('LMP-4001', 'WH-MAD', 12), ('LMP-4001', 'WH-BCN', 8),  ('LMP-4001', 'WH-PAR', 6),
  ('LMP-4002', 'WH-MAD', 30), ('LMP-4002', 'WH-BCN', 21), ('LMP-4002', 'WH-PAR', 15),
  ('LMP-4010', 'WH-MAD', 0),  ('LMP-4010', 'WH-BCN', 7),  ('LMP-4010', 'WH-PAR', 3),
  ('LMP-4020', 'WH-MAD', 18), ('LMP-4020', 'WH-BCN', 10), ('LMP-4020', 'WH-PAR', 9),
  ('DEC-5001', 'WH-MAD', 11), ('DEC-5001', 'WH-BCN', 6),  ('DEC-5001', 'WH-PAR', 4),
  ('DEC-5002', 'WH-MAD', 4),  ('DEC-5002', 'WH-BCN', 0),  ('DEC-5002', 'WH-PAR', 2),
  ('DEC-5010', 'WH-MAD', 44), ('DEC-5010', 'WH-BCN', 38), ('DEC-5010', 'WH-PAR', 20),
  ('DEC-5020', 'WH-MAD', 60), ('DEC-5020', 'WH-BCN', 50), ('DEC-5020', 'WH-PAR', 33),
  ('OUT-6001', 'WH-MAD', 7),  ('OUT-6001', 'WH-BCN', 5),  ('OUT-6001', 'WH-PAR', 0),
  ('OUT-6002', 'WH-MAD', 2),  ('OUT-6002', 'WH-BCN', 0),  ('OUT-6002', 'WH-PAR', 0),
  ('OUT-6010', 'WH-MAD', 9),  ('OUT-6010', 'WH-BCN', 6),  ('OUT-6010', 'WH-PAR', 4),
  ('OUT-6020', 'WH-MAD', 25), ('OUT-6020', 'WH-BCN', 17), ('OUT-6020', 'WH-PAR', 11);
