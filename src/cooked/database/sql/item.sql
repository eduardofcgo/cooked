
-- :name get-item-by-id :query :1
select * from item where id = :item-id


-- :name get-item-by-url :query :1
select * from item where account = :account and url = :url


-- :name create-item :! :n
insert into item (id, account, url, title, description, extra, image_url, video_url)
values (:id, :account, :url, :title, :description, :extra, :image-url, :video-url)


-- :name user-items :many
select title, description, url from item where account = :account

-- :name item-keywords :many
select keyword from item_keyword where item_id = :item-id
order by keyword


-- :name item-owner :query :1
select account from item where id = :item-id


-- :name count-account-items :many
select item_keyword.keyword, count(item_keyword.keyword) as keyword_count from item_keyword
inner join (
  select item.id from item
  --~ (when-not (empty? (:keywords params)) "inner join item_keyword on item_keyword.item_id = item.id")
  where item.account = :account
  --~ (when (:query params) "and item.ts @@ websearch_to_tsquery('en', :query)")
  /*~ (when-not (empty? (:keywords params))
                (str "and item_keyword.keyword in (:value*:keywords)
                      group by item_keyword.item_id, item.id
                      having count(item_keyword.keyword) = " (count (:keywords params)))) ~*/
) as found_item on item_keyword.item_id = found_item.id
group by item_keyword.keyword
order by keyword_count desc, item_keyword.keyword desc


-- :name query-account-items :many
select item.id, item.title, item.image_url from item
--~ (when-not (empty? (:keywords params)) "inner join item_keyword on item_keyword.item_id = item.id")
where item.account = :account
--~ (when (:query params) "and (item.ts @@ websearch_to_tsquery('en', :query))")
/*~ (when-not (empty? (:keywords params))
              (str "and item_keyword.keyword in (:value*:keywords)
                    group by item_keyword.item_id, item.id
                    having count(item_keyword.keyword) = " (count (:keywords params)))) ~*/
/*~ (if (:query params)
        "order by ts_rank_cd(item.ts, websearch_to_tsquery('en', :query)), item.created desc"
        "order by item.created desc") ~*/
limit :limit offset :offset
