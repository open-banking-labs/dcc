package api

import (
	"encoding/json"
	"time"
)

// Me is GET /api/me.
type Me struct {
	Subject     string   `json:"subject"`
	Tenant      string   `json:"tenant"`
	Permissions []string `json:"permissions"`
}

// Page mirrors Spring Data's Page envelope.
type Page[T any] struct {
	Content       []T   `json:"content"`
	TotalElements int64 `json:"totalElements"`
	TotalPages    int   `json:"totalPages"`
	Number        int   `json:"number"`
	Size          int   `json:"size"`
	First         bool  `json:"first"`
	Last          bool  `json:"last"`
	Empty         bool  `json:"empty"`
}

// FieldChange is one attribute difference between two snapshots.
type FieldChange struct {
	Field  string `json:"field"`
	Before string `json:"before"`
	After  string `json:"after"`
}

// DiffView is the /api diff envelope.
type DiffView struct {
	FromVersion string        `json:"fromVersion"`
	ToVersion   string        `json:"toVersion"`
	ChangeType  string        `json:"changeType"`
	Changes     []FieldChange `json:"changes"`
}

// BumpSuggestion is POST /cli/v1/bump.
type BumpSuggestion struct {
	Level   string        `json:"level"`
	Reasons []string      `json:"reasons"`
	Changes []FieldChange `json:"changes"`
}

// ValidationResult is POST /cli/v1/validate.
type ValidationResult struct {
	Valid  bool     `json:"valid"`
	Errors []string `json:"errors"`
}

// ExportFile is one generated file.
type ExportFile struct {
	Path    string `json:"path"`
	Content string `json:"content"`
}

// ExportResponse is POST /cli/v1/export/*.
type ExportResponse struct {
	Files []ExportFile `json:"files"`
}

// DriftFinding is one drifted artifact.
type DriftFinding struct {
	ArtifactType string `json:"artifactType"`
	Code         string `json:"code"`
	Version      string `json:"version"`
}

// DriftResponse is POST /cli/v1/drift.
type DriftResponse struct {
	Drifted []DriftFinding `json:"drifted"`
}

// AuditEntry is one audit-trail row.
type AuditEntry struct {
	ID            int64     `json:"id"`
	TenantID      string    `json:"tenantId"`
	EntityType    string    `json:"entityType"`
	EntityID      int64     `json:"entityId"`
	EntityCode    string    `json:"entityCode"`
	Action        string    `json:"action"`
	Actor         string    `json:"actor"`
	Reason        string    `json:"reason"`
	ChangedFields string    `json:"changedFields"`
	BeforeState   string    `json:"beforeState"`
	AfterState    string    `json:"afterState"`
	TraceID       string    `json:"traceId"`
	PreviousHash  string    `json:"previousHash"`
	Hash          string    `json:"hash"`
	OccurredAt    time.Time `json:"occurredAt"`
}

// VerifyResult is POST /cli/v1/audit/verify.
type VerifyResult struct {
	Intact   bool     `json:"intact"`
	Problems []string `json:"problems"`
}

// StandardView is GET /api/standards/{id}.
type StandardView struct {
	ID             int64           `json:"id"`
	TenantID       string          `json:"tenantId"`
	EnvironmentID  int64           `json:"environmentId"`
	ApplicationID  int64           `json:"applicationId"`
	Code           string          `json:"code"`
	Category       string          `json:"category"`
	Status         string          `json:"status"`
	CurrentVersion string          `json:"currentVersion"`
	Content        json.RawMessage `json:"content"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// StandardSummary is one row of GET /api/standards/page.
type StandardSummary struct {
	ID             int64     `json:"id"`
	EnvironmentID  int64     `json:"environmentId"`
	ApplicationID  int64     `json:"applicationId"`
	Code           string    `json:"code"`
	Name           string    `json:"name"`
	Category       string    `json:"category"`
	DataType       string    `json:"dataType"`
	Status         string    `json:"status"`
	CurrentVersion string    `json:"currentVersion"`
	UpdatedAt      time.Time `json:"updatedAt"`
}

// TableView is GET /api/tables/{id}.
type TableView struct {
	ID             int64           `json:"id"`
	TenantID       string          `json:"tenantId"`
	EnvironmentID  int64           `json:"environmentId"`
	ApplicationID  int64           `json:"applicationId"`
	Code           string          `json:"code"`
	Status         string          `json:"status"`
	CurrentVersion string          `json:"currentVersion"`
	Content        json.RawMessage `json:"content"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// InterfaceView is GET /api/interfaces/{id}.
type InterfaceView struct {
	ID             int64           `json:"id"`
	TenantID       string          `json:"tenantId"`
	EnvironmentID  int64           `json:"environmentId"`
	ApplicationID  int64           `json:"applicationId"`
	InterfaceNo    string          `json:"interfaceNo"`
	TemplateID     *int64          `json:"templateId"`
	Status         string          `json:"status"`
	CurrentVersion string          `json:"currentVersion"`
	Content        json.RawMessage `json:"content"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// TemplateView is GET /api/templates/{id}.
type TemplateView struct {
	ID             int64           `json:"id"`
	TenantID       string          `json:"tenantId"`
	EnvironmentID  int64           `json:"environmentId"`
	ApplicationID  int64           `json:"applicationId"`
	Code           string          `json:"code"`
	Status         string          `json:"status"`
	CurrentVersion string          `json:"currentVersion"`
	Content        json.RawMessage `json:"content"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// VersionView is one history row shared by all four artifact kinds.
type VersionView struct {
	ID                  int64           `json:"id"`
	Version             string          `json:"version"`
	ChangeType          string          `json:"changeType"`
	ChangeSummary       string          `json:"changeSummary"`
	Content             json.RawMessage `json:"content"`
	SourceEnvironmentID *int64          `json:"sourceEnvironmentId"`
	SourceVersion       string          `json:"sourceVersion"`
	CreatedAt           time.Time       `json:"createdAt"`
	ContentHash         string          `json:"contentHash"`
	ParentHash          string          `json:"parentHash"`
	Author              string          `json:"author"`
	Message             string          `json:"message"`
}

// MigrationOrderView is GET /api/migrations/{id}.
type MigrationOrderView struct {
	ID                  int64     `json:"id"`
	TenantID            string    `json:"tenantId"`
	SourceEnvironmentID int64     `json:"sourceEnvironmentId"`
	TargetEnvironmentID int64     `json:"targetEnvironmentId"`
	ArtifactType        string    `json:"artifactType"`
	ArtifactID          int64     `json:"artifactId"`
	ArtifactCode        string    `json:"artifactCode"`
	SourceVersion       string    `json:"sourceVersion"`
	Status              string    `json:"status"`
	Note                string    `json:"note"`
	TargetArtifactID    *int64    `json:"targetArtifactId"`
	TargetVersion       string    `json:"targetVersion"`
	SubmittedBy         string    `json:"submittedBy"`
	CreatedAt           time.Time `json:"createdAt"`
	UpdatedAt           time.Time `json:"updatedAt"`
}

// ApplicationMigrationResult is POST /api/migrations/application.
type ApplicationMigrationResult struct {
	Created []MigrationOrderView `json:"created"`
	Skipped []string             `json:"skipped"`
}

// EnvironmentView is GET /api/environments/{id}.
type EnvironmentView struct {
	ID          int64     `json:"id"`
	TenantID    string    `json:"tenantId"`
	Code        string    `json:"code"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	SortOrder   int       `json:"sortOrder"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// ApplicationView is GET /api/applications/{id}.
type ApplicationView struct {
	ID          int64     `json:"id"`
	TenantID    string    `json:"tenantId"`
	Code        string    `json:"code"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// TableDdl is GET /api/tables/{id}/ddl.
type TableDdl struct {
	Dialect        string `json:"dialect"`
	DDL            string `json:"ddl"`
	FlywayFileName string `json:"flywayFileName"`
	FlywayScript   string `json:"flywayScript"`
}

// Restored is the synthetic response for a 204 restore.
type Restored struct {
	Restored bool   `json:"restored"`
	Type     string `json:"type"`
	ID       int64  `json:"id"`
}
