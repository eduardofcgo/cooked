
-- :name account-with-username :query :1
select * from account
where username = :username


-- :name account-with-username-password :query :1
select * from account
where username = :username and password = :password
