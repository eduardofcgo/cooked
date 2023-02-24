CREATE EXTENSION unaccent;
--;;
CREATE TEXT SEARCH CONFIGURATION en ( COPY = english );
--;;
ALTER TEXT SEARCH CONFIGURATION en ALTER MAPPING
FOR hword, hword_part, word WITH unaccent, english_stem;
--;;
alter table item add column language regconfig not null default 'en'::regconfig;
--;;
alter table item_keyword add column language regconfig not null default 'en'::regconfig;
--;;
alter table item add column ts tsvector
    generated always as (setweight(to_tsvector(language, title::text), 'A') || 
                         setweight(to_tsvector(language, coalesce(description::text, '')), 'B') ||
                         setweight(to_tsvector(language, coalesce(extra::text, '')), 'B')) stored;
--;;
create index item_ts_idx on item using gin(ts);
--;;
alter table item_keyword add column ts tsvector
    generated always as (to_tsvector(language, keyword::text)) stored;
--;;
create index item_keyword_ts_idx on item_keyword using gin(ts);
