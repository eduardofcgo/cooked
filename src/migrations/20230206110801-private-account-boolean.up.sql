alter table account alter column private drop default;
--;;
alter table account alter column private type boolean using private::boolean;
--;;
alter table account alter column private set default false;