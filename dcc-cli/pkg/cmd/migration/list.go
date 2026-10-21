package migration

import (
	"fmt"
	"io"
	"net/url"
	"strconv"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/tableprinter"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdList returns `dcc migration list`.
func NewCmdList(f *factory.Factory) *cobra.Command {
	var targetEnvironmentID int64
	var status string
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List migration orders",
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

			query := url.Values{}
			if targetEnvironmentID > 0 {
				query.Set("targetEnvironmentId", strconv.FormatInt(targetEnvironmentID, 10))
			}
			if status != "" {
				query.Set("status", status)
			}

			var orders []api.MigrationOrderView
			if err := client.Get(cmd.Context(), "migrations", query, &orders); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, orders, func(w io.Writer) error {
				table := tableprinter.New(w, f.IOStreams.IsStdoutTTY())
				table.Header("ID", "Artifact", "Code", "Source", "Target", "Status", "Submitted By", "Updated")
				for _, order := range orders {
					table.Row(
						strconv.FormatInt(order.ID, 10),
						order.ArtifactType,
						order.ArtifactCode,
						strconv.FormatInt(order.SourceEnvironmentID, 10),
						strconv.FormatInt(order.TargetEnvironmentID, 10),
						order.Status,
						order.SubmittedBy,
						order.UpdatedAt.UTC().Format("2006-01-02 15:04:05"),
					)
				}
				return table.Render()
			})
		},
	}
	cmd.Flags().Int64Var(&targetEnvironmentID, "target-environment-id", 0, "Filter by target environment")
	cmd.Flags().StringVar(&status, "status", "", "Filter by status")
	return cmd
}

// NewCmdView returns `dcc migration view`.
func NewCmdView(f *factory.Factory) *cobra.Command {
	return &cobra.Command{
		Use:   "view <id>",
		Short: "View one migration order",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var order api.MigrationOrderView
			if err := client.Get(cmd.Context(), fmt.Sprintf("migrations/%d", id), nil, &order); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, order, func(w io.Writer) error {
				return printOrder(w, order)
			})
		},
	}
}

func parseID(args []string) (int64, error) {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil || id <= 0 {
		return 0, cmdutil.UsageError("invalid id %q: expected a positive integer", args[0])
	}
	return id, nil
}
