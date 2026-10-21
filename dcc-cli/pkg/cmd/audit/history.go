package audit

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tableprinter"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdHistory returns `dcc audit history`.
func NewCmdHistory(f *factory.Factory) *cobra.Command {
	var entityType string
	var entityID int64
	cmd := &cobra.Command{
		Use:   "history",
		Short: "Show the audit history of one row",
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

			body := map[string]any{"entityType": entityType, "entityId": entityID}
			var entries []api.AuditEntry
			if err := client.Command(cmd.Context(), "audit/history", body, &entries); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, entries, func(w io.Writer) error {
				return renderEntries(w, f.IOStreams.IsStdoutTTY(), entries)
			})
		},
	}
	cmd.Flags().StringVar(&entityType, "entity-type", "", "Entity type, e.g. DATA_STANDARD")
	cmd.Flags().Int64Var(&entityID, "entity-id", 0, "Entity id")
	_ = cmd.MarkFlagRequired("entity-type")
	_ = cmd.MarkFlagRequired("entity-id")
	return cmd
}

func renderEntries(w io.Writer, tty bool, entries []api.AuditEntry) error {
	table := tableprinter.New(w, tty)
	table.Header("ID", "Action", "Actor", "Reason", "Changed", "Occurred")
	for _, entry := range entries {
		table.Row(
			fmt.Sprint(entry.ID),
			entry.Action,
			entry.Actor,
			entry.Reason,
			entry.ChangedFields,
			entry.OccurredAt.UTC().Format("2006-01-02 15:04:05"),
		)
	}
	return table.Render()
}
