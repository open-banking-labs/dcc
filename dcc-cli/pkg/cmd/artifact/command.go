package artifact

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

func modelBody(kind Kind, id int64, version string) map[string]any {
	return map[string]any{
		"type":    kind.ArtifactType,
		"modelId": id,
		"version": version,
	}
}

func rangeBody(kind Kind, id int64, from, to string) map[string]any {
	return map[string]any{
		"type":        kind.ArtifactType,
		"modelId":     id,
		"fromVersion": from,
		"toVersion":   to,
	}
}

// NewCmdHash returns the noun's hash command.
func NewCmdHash(f *factory.Factory, kind Kind) *cobra.Command {
	var version string
	cmd := &cobra.Command{
		Use:   "hash <id>",
		Short: "Print the content hash of one " + kind.Noun + " version",
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

			var response struct {
				Hash string `json:"hash"`
			}
			if err := client.Command(cmd.Context(), "hash", modelBody(kind, id, version), &response); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
				_, err := fmt.Fprintln(w, response.Hash)
				return err
			})
		},
	}
	cmd.Flags().StringVar(&version, "version", "", "Version")
	_ = cmd.MarkFlagRequired("version")
	return cmd
}

// NewCmdBump returns the noun's bump command.
func NewCmdBump(f *factory.Factory, kind Kind) *cobra.Command {
	var from, to string
	cmd := &cobra.Command{
		Use:   "bump <id>",
		Short: "Suggest the semantic version bump between two versions",
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

			var suggestion api.BumpSuggestion
			if err := client.Command(cmd.Context(), "bump", rangeBody(kind, id, from, to), &suggestion); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, suggestion, func(w io.Writer) error {
				if _, err := fmt.Fprintf(w, "suggested bump: %s\n", suggestion.Level); err != nil {
					return err
				}
				for _, reason := range suggestion.Reasons {
					if _, err := fmt.Fprintf(w, "- %s\n", reason); err != nil {
						return err
					}
				}
				return nil
			})
		},
	}
	cmd.Flags().StringVar(&from, "from", "", "Source version")
	cmd.Flags().StringVar(&to, "to", "", "Target version")
	_ = cmd.MarkFlagRequired("from")
	_ = cmd.MarkFlagRequired("to")
	return cmd
}

// NewCmdValidate returns the noun's validation command.
func NewCmdValidate(f *factory.Factory, kind Kind) *cobra.Command {
	var version string
	cmd := &cobra.Command{
		Use:   "validate <id>",
		Short: "Verify that a stored snapshot still matches its hash",
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

			var result api.ValidationResult
			if err := client.Command(cmd.Context(), "validate", modelBody(kind, id, version), &result); err != nil {
				return cmdutil.WrapError(err)
			}
			if err := out.Print(f.IOStreams.Out, result, func(w io.Writer) error {
				if result.Valid {
					_, err := fmt.Fprintln(w, "valid")
					return err
				}
				if _, err := fmt.Fprintln(w, "invalid"); err != nil {
					return err
				}
				for _, problem := range result.Errors {
					if _, err := fmt.Fprintf(w, "- %s\n", problem); err != nil {
						return err
					}
				}
				return nil
			}); err != nil {
				return err
			}
			if !result.Valid {
				return cmdutil.SilentExit(1)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&version, "version", "", "Version")
	_ = cmd.MarkFlagRequired("version")
	return cmd
}
