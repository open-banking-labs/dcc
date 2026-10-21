// Package tenantresource implements the shared CRUD verb set for tenant-scoped
// resources that have no versioning (environments and applications).
package tenantresource

// Kind describes one tenant resource.
type Kind struct {
	Noun        string
	Article     string
	APIPath     string
	ListColumns []Column
}

// Column is one human-readable list column.
type Column struct {
	Header string
	Key    string
}

// The supported resources.
var (
	Environment = Kind{
		Noun:    "environment",
		Article: "an",
		APIPath: "environments",
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Code", Key: "code"},
			{Header: "Name", Key: "name"},
			{Header: "Sort", Key: "sortOrder"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
	Application = Kind{
		Noun:    "application",
		Article: "a",
		APIPath: "applications",
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Code", Key: "code"},
			{Header: "Name", Key: "name"},
			{Header: "Description", Key: "description"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
)
