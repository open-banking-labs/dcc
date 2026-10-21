package migration

import (
	"fmt"
	"io"
	"net/http"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/confirmation"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type actionOptions struct {
	Message cmdutil.MessageOptions
	Yes     bool
}

func newActionCmd(f *factory.Factory, action, short string, needsConfirmation bool) *cobra.Command {
	opts := &actionOptions{}
	cmd := &cobra.Command{
		Use:   action + " <id>",
		Short: short,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			if needsConfirmation {
				if err := confirmation.Confirm(f.IOStreams,
					fmt.Sprintf("%s migration #%d?", action, id), opts.Yes); err != nil {
					return err
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

			var order api.MigrationOrderView
			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPost,
				Path:          client.APIPath(fmt.Sprintf("migrations/%d/%s", id, action)),
				ChangeMessage: message.HeaderValue(),
				Out:           &order,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, order, func(w io.Writer) error {
				_, err := fmt.Fprintf(w, "migration #%d: %s\n", order.ID, order.Status)
				return err
			})
		},
	}
	if needsConfirmation {
		cmd.Flags().BoolVar(&opts.Yes, "yes", false, "Skip the confirmation prompt")
	}
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	return cmd
}

// NewCmdSubmit returns `dcc migration submit`.
func NewCmdSubmit(f *factory.Factory) *cobra.Command {
	return newActionCmd(f, "submit", "Submit a migration order for approval", false)
}

// NewCmdApprove returns `dcc migration approve`.
func NewCmdApprove(f *factory.Factory) *cobra.Command {
	return newActionCmd(f, "approve", "Approve a migration order", true)
}

// NewCmdReject returns `dcc migration reject`.
func NewCmdReject(f *factory.Factory) *cobra.Command {
	return newActionCmd(f, "reject", "Reject a migration order", true)
}

// NewCmdExecute returns `dcc migration execute`.
func NewCmdExecute(f *factory.Factory) *cobra.Command {
	return newActionCmd(f, "execute", "Execute an approved migration order", true)
}

// NewCmdRollback returns `dcc migration rollback`.
func NewCmdRollback(f *factory.Factory) *cobra.Command {
	return newActionCmd(f, "rollback", "Roll back an executed migration order", true)
}
