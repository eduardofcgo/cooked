drop extention unaccent;
--;;
drop text search configuration en;
--;;
alter table item drop column language;
--;;
alter table item_keyword drop column language;
--;;
alter table item drop column ts;
--;;
alter table item_keyword drop column ts;
--;;
drop index item_ts_idx;
--;;
drop index item_keyword_ts_idx;