package [(${packageName})];

import java.util.List;

/** Generated DTO for [(${name})]. */
public record [(${name})](
[# th:each="f,it : ${fields}"]        [(${f.type})] [(${f.name})][# th:if="${!it.last}"],[/]
[/]) {
}
