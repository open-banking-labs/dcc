// Package artifact implements the shared verb set for the four artifact nouns
// (standard, table, interface, template). Each noun package only declares its
// Kind; the command bodies live here so endpoint, flag and output behaviour stay
// consistent across nouns.
package artifact

// Kind describes one artifact noun.
type Kind struct {
	// Noun is the command name, e.g. "standard".
	Noun string
	// Article is the indefinite article used in help text ("a" / "an").
	Article string
	// APIPath is the /api resource path, e.g. "standards".
	APIPath string
	// ArtifactType is the server enum used by /cli/v1 bodies.
	ArtifactType string
	// PageList selects GET <APIPath>/page over GET <APIPath>.
	PageList bool
	// ExportFormat is the only supported /cli/v1/export target ("" = none).
	ExportFormat string
	// SupportsDDL enables table ddl / ddl-alter.
	SupportsDDL bool
	// ListColumns drives the human table.
	ListColumns []Column
}

// Column is one human-readable list column.
type Column struct {
	Header string
	Key    string
}

// The four artifact kinds.
var (
	Standard = Kind{
		Noun:         "standard",
		Article:      "a",
		APIPath:      "standards",
		ArtifactType: "DATA_STANDARD",
		PageList:     true,
		ExportFormat: "java",
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Code", Key: "code"},
			{Header: "Name", Key: "name"},
			{Header: "Category", Key: "category"},
			{Header: "Status", Key: "status"},
			{Header: "Version", Key: "currentVersion"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
	Table = Kind{
		Noun:         "table",
		Article:      "a",
		APIPath:      "tables",
		ArtifactType: "TABLE_STRUCTURE",
		ExportFormat: "sql",
		SupportsDDL:  true,
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Code", Key: "code"},
			{Header: "Status", Key: "status"},
			{Header: "Version", Key: "currentVersion"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
	Interface = Kind{
		Noun:         "interface",
		Article:      "an",
		APIPath:      "interfaces",
		ArtifactType: "INTERFACE",
		ExportFormat: "java",
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Interface No", Key: "interfaceNo"},
			{Header: "Template", Key: "templateId"},
			{Header: "Status", Key: "status"},
			{Header: "Version", Key: "currentVersion"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
	Template = Kind{
		Noun:         "template",
		Article:      "an",
		APIPath:      "templates",
		ArtifactType: "INTERFACE_TEMPLATE",
		ListColumns: []Column{
			{Header: "ID", Key: "id"},
			{Header: "Code", Key: "code"},
			{Header: "Status", Key: "status"},
			{Header: "Version", Key: "currentVersion"},
			{Header: "Updated", Key: "updatedAt"},
		},
	}
)

// Attach is implemented in attach.go.
