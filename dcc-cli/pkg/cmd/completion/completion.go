// Package completion implements shell completion generation.
package completion

import (
	"io"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdCompletion returns the completion command group.
func NewCmdCompletion(f *factory.Factory) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "completion",
		Short: "Generate shell completion scripts",
		Long: `Generate a completion script for dcc.

  bash:       source <(dcc completion bash)
  zsh:        dcc completion zsh > "${fpath[1]}/_dcc"
  fish:       dcc completion fish | source
  powershell: dcc completion powershell | Out-String | Invoke-Expression`,
	}
	cmd.AddCommand(
		shellCmd("bash", "Generate bash completion", func(root *cobra.Command, w io.Writer) error {
			return root.GenBashCompletionV2(w, true)
		}),
		shellCmd("zsh", "Generate zsh completion", func(root *cobra.Command, w io.Writer) error {
			return root.GenZshCompletion(w)
		}),
		shellCmd("fish", "Generate fish completion", func(root *cobra.Command, w io.Writer) error {
			return root.GenFishCompletion(w, true)
		}),
		shellCmd("powershell", "Generate PowerShell completion", func(root *cobra.Command, w io.Writer) error {
			return root.GenPowerShellCompletionWithDesc(w)
		}),
	)
	return cmd
}

func shellCmd(name, short string, gen func(*cobra.Command, io.Writer) error) *cobra.Command {
	return &cobra.Command{
		Use:   name,
		Short: short,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return gen(cmd.Root(), cmd.OutOrStdout())
		},
	}
}
