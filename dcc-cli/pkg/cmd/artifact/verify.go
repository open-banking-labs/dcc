package artifact

import (
	"fmt"
	"io"
	"net/url"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdVerify returns the noun's snapshot verification command.
func NewCmdVerify(f *factory.Factory, kind Kind) *cobra.Command {
	var version string
	cmd := &cobra.Command{
		Use:   "verify <id>",
		Short: "Verify one stored snapshot of " + article(kind) + " " + kind.Noun,
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

			var valid bool
			path := fmt.Sprintf("%s/%d/versions/%s/verify", kind.APIPath, id, url.PathEscape(version))
			if err := client.Get(cmd.Context(), path, nil, &valid); err != nil {
				return cmdutil.WrapError(err)
			}
			result := map[string]any{"valid": valid}
			if err := out.Print(f.IOStreams.Out, result, func(w io.Writer) error {
				status := "invalid: content hash drifted"
				if valid {
					status = "valid: content hash matches"
				}
				_, err := fmt.Fprintln(w, status)
				return err
			}); err != nil {
				return err
			}
			if !valid {
				return cmdutil.SilentExit(1)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&version, "version", "", "Version to verify")
	_ = cmd.MarkFlagRequired("version")
	return cmd
}
