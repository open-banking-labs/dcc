package audit

import (
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdSearch returns `dcc audit search`.
func NewCmdSearch(f *factory.Factory) *cobra.Command {
	var (
		entityType string
		entityID   int64
		actor      string
		action     string
		from       string
		to         string
		page       int
		size       int
	)
	cmd := &cobra.Command{
		Use:   "search",
		Short: "Search the audit trail (newest first)",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			body := map[string]any{}
			if entityType != "" {
				body["entityType"] = entityType
			}
			if entityID > 0 {
				body["entityId"] = entityID
			}
			if actor != "" {
				body["actor"] = actor
			}
			if action != "" {
				body["action"] = action
			}
			if from != "" {
				body["from"] = from
			}
			if to != "" {
				body["to"] = to
			}
			body["page"] = page
			body["size"] = size

			var result api.Page[api.AuditEntry]
			if err := client.Command(cmd.Context(), "audit/search", body, &result); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, result, func(w io.Writer) error {
				return renderEntries(w, f.IOStreams.IsStdoutTTY(), result.Content)
			})
		},
	}
	cmd.Flags().StringVar(&entityType, "entity-type", "", "Filter by entity type")
	cmd.Flags().Int64Var(&entityID, "entity-id", 0, "Filter by entity id")
	cmd.Flags().StringVar(&actor, "actor", "", "Filter by actor")
	cmd.Flags().StringVar(&action, "action", "", "Filter by audit action")
	cmd.Flags().StringVar(&from, "from", "", "Lower bound, RFC3339")
	cmd.Flags().StringVar(&to, "to", "", "Upper bound, RFC3339")
	cmd.Flags().IntVar(&page, "page", 0, "Zero-based page number")
	cmd.Flags().IntVar(&size, "size", 50, "Page size (server caps at 200)")
	return cmd
}
