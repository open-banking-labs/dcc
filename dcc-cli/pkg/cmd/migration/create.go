package migration

import (
	"fmt"
	"io"
	"net/http"
	"strconv"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type createOptions struct {
	SourceEnvironmentID int64
	TargetEnvironmentID int64
	ApplicationID       int64
	ArtifactType        string
	ArtifactID          int64
	ArtifactTypes       []string
	Message             cmdutil.MessageOptions
}

// NewCmdCreate returns `dcc migration create`.
func NewCmdCreate(f *factory.Factory) *cobra.Command {
	opts := &createOptions{}
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a migration order (single artifact or whole application)",
		Long: `Create a migration order.

Single artifact:
  dcc migration create --artifact-type TABLE_STRUCTURE --artifact-id 7 \
    --source-environment-id 1 --target-environment-id 2 -m "feat(migration): move table"

Whole application:
  dcc migration create --application-id 3 --source-environment-id 1 \
    --target-environment-id 2 -m "feat(migration): publish application"`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if opts.SourceEnvironmentID <= 0 || opts.TargetEnvironmentID <= 0 {
				return cmdutil.UsageError("--source-environment-id and --target-environment-id are required")
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}

			applicationMode := opts.ApplicationID > 0
			if !applicationMode {
				if opts.ArtifactType == "" || opts.ArtifactID <= 0 {
					return cmdutil.UsageError(
						"either --application-id or --artifact-type and --artifact-id are required")
				}
			}

			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			note := truncateRunes(message.Raw, 512)
			if applicationMode {
				body := map[string]any{
					"sourceEnvironmentId": opts.SourceEnvironmentID,
					"targetEnvironmentId": opts.TargetEnvironmentID,
					"applicationId":       opts.ApplicationID,
					"artifactTypes":       opts.ArtifactTypes,
					"note":                note,
				}
				var result api.ApplicationMigrationResult
				if err := client.Do(cmd.Context(), api.Request{
					Method:        http.MethodPost,
					Path:          client.APIPath("migrations/application"),
					Body:          body,
					ChangeMessage: message.HeaderValue(),
					Out:           &result,
				}); err != nil {
					return cmdutil.WrapError(err)
				}
				return out.Print(f.IOStreams.Out, result, func(w io.Writer) error {
					fmt.Fprintf(w, "created %d migration order(s)\n", len(result.Created))
					for _, order := range result.Created {
						fmt.Fprintf(w, "- #%d %s %s\n", order.ID, order.ArtifactType, order.ArtifactCode)
					}
					if len(result.Skipped) > 0 {
						fmt.Fprintf(w, "skipped %d already-current artifact(s)\n", len(result.Skipped))
					}
					return nil
				})
			}

			body := map[string]any{
				"artifactType":        opts.ArtifactType,
				"artifactId":          opts.ArtifactID,
				"sourceEnvironmentId": opts.SourceEnvironmentID,
				"targetEnvironmentId": opts.TargetEnvironmentID,
				"note":                note,
			}
			var order api.MigrationOrderView
			if err := client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPost,
				Path:          client.APIPath("migrations"),
				Body:          body,
				ChangeMessage: message.HeaderValue(),
				Out:           &order,
			}); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, order, func(w io.Writer) error {
				return printOrder(w, order)
			})
		},
	}
	cmd.Flags().Int64Var(&opts.SourceEnvironmentID, "source-environment-id", 0, "Source environment id")
	cmd.Flags().Int64Var(&opts.TargetEnvironmentID, "target-environment-id", 0, "Target environment id")
	cmd.Flags().Int64Var(&opts.ApplicationID, "application-id", 0, "Migrate every artifact of this application")
	cmd.Flags().StringVar(&opts.ArtifactType, "artifact-type", "", "Artifact type for a single-artifact order")
	cmd.Flags().Int64Var(&opts.ArtifactID, "artifact-id", 0, "Artifact id for a single-artifact order")
	cmd.Flags().StringSliceVar(&opts.ArtifactTypes, "artifact-types", nil,
		"Limit an application migration to these artifact types")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("source-environment-id")
	_ = cmd.MarkFlagRequired("target-environment-id")
	return cmd
}

func truncateRunes(value string, max int) string {
	runes := []rune(value)
	if len(runes) <= max {
		return value
	}
	return string(runes[:max])
}

func printOrder(w io.Writer, order api.MigrationOrderView) error {
	return output.KeyValues(w, [][2]string{
		{"id", strconv.FormatInt(order.ID, 10)},
		{"artifact", order.ArtifactType},
		{"code", order.ArtifactCode},
		{"source", fmt.Sprintf("%d (%s)", order.SourceEnvironmentID, order.SourceVersion)},
		{"target", fmt.Sprintf("%d (%s)", order.TargetEnvironmentID, order.TargetVersion)},
		{"status", order.Status},
		{"submitted by", order.SubmittedBy},
		{"note", order.Note},
	})
}
