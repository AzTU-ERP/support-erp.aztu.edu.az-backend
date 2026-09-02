-- ============================================================
-- Starting module/section catalogue.
-- This is seed data, not configuration frozen in code: an admin may add, rename or deactivate
-- rows afterwards and the frontend picks the change up from GET /api/support/config/modules.
-- Names are Azerbaijani, the shell's default locale; the frontend falls back to them when it
-- has no translation of its own for a section code.
-- ============================================================

INSERT INTO support_modules (code, name, route_prefix, sort_order) VALUES
  ('LMS',      'LMS',       '/lms',       1),
  ('HR',       'HR',        '/hr',        2),
  ('LIBRARY',  'Kitabxana', '/library',   3),
  ('FINANCE',  'Maliyyə',   '/finance',   4),
  ('EXAM',     'İmtahan',   '/exam',      5),
  ('TURNIKET', 'Turniket',  '/turnstile', 6);

INSERT INTO support_sections (module_code, code, name, sort_order) VALUES
  ('LMS', 'davamiyyet',         'Davamiyyət',            1),
  ('LMS', 'qiymetlendirme',     'Qiymətləndirmə',        2),
  ('LMS', 'kurs_materiallari',  'Kurs materialları',     3),
  ('LMS', 'tapsiriqlar',        'Tapşırıqlar',           4),
  ('LMS', 'elanlar',            'Elanlar',               5),
  ('LMS', 'diger',              'Digər',                 99),

  ('HR', 'isci_melumatlari',    'İşçi məlumatları',      1),
  ('HR', 'mezuniyyet',          'Məzuniyyət',            2),
  ('HR', 'emek_haqqi',          'Əmək haqqı',            3),
  ('HR', 'struktur',            'Struktur',              4),
  ('HR', 'diger',               'Digər',                 99),

  ('LIBRARY', 'kitab_axtarisi', 'Kitab axtarışı',        1),
  ('LIBRARY', 'rezervasiya',    'Rezervasiya',           2),
  ('LIBRARY', 'borc_qaytarma',  'Borc / qaytarma',       3),
  ('LIBRARY', 'e_resurslar',    'Elektron resurslar',    4),
  ('LIBRARY', 'diger',          'Digər',                 99),

  ('FINANCE', 'odenisler',      'Ödənişlər',             1),
  ('FINANCE', 'borclar',        'Borclar',               2),
  ('FINANCE', 'hesab_faktura',  'Hesab-faktura',         3),
  ('FINANCE', 'tequadler',      'Təqaüdlər',             4),
  ('FINANCE', 'diger',          'Digər',                 99),

  ('EXAM', 'imtahan_cedveli',   'İmtahan cədvəli',       1),
  ('EXAM', 'imtahan_neticeleri','İmtahan nəticələri',    2),
  ('EXAM', 'qeydiyyat',         'Qeydiyyat',             3),
  ('EXAM', 'appelyasiya',       'Apellyasiya',           4),
  ('EXAM', 'diger',             'Digər',                 99),

  ('TURNIKET', 'giris_cixis',   'Giriş / çıxış',         1),
  ('TURNIKET', 'kart_problemi', 'Kart problemi',         2),
  ('TURNIKET', 'icaze',         'İcazə',                 3),
  ('TURNIKET', 'hesabat',       'Hesabat',               4),
  ('TURNIKET', 'diger',         'Digər',                 99);
