CREATE TABLE [(${qualified})] (
[# th:each="d,it : ${definitions}"]  [(${d.definition})][# th:if="${!it.last}"],[/]
[/]);
[# th:if="${tableComment != null}"]COMMENT ON TABLE [(${qualified})] IS [(${tableComment})];
[/][# th:each="cc : ${columnComments}"]COMMENT ON COLUMN [(${cc.column})] IS [(${cc.comment})];
[/][# th:each="ix : ${indexes}"][(${ix})]
[/]